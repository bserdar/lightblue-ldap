/*
 Copyright 2014 Red Hat, Inc. and/or its affiliates.

 This file is part of lightblue.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.redhat.lightblue.crud.ldap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.redhat.lightblue.common.ldap.DBResolver;
import com.redhat.lightblue.common.ldap.LdapDataStore;
import com.redhat.lightblue.crud.CRUDController;
import com.redhat.lightblue.crud.CRUDDeleteResponse;
import com.redhat.lightblue.crud.CRUDFindResponse;
import com.redhat.lightblue.crud.CRUDInsertionResponse;
import com.redhat.lightblue.crud.CRUDOperationContext;
import com.redhat.lightblue.crud.CRUDSaveResponse;
import com.redhat.lightblue.crud.CRUDUpdateResponse;
import com.redhat.lightblue.crud.DocCtx;
import com.redhat.lightblue.crud.ldap.translator.unboundid.FilterTranslator;
import com.redhat.lightblue.crud.ldap.translator.unboundid.ResultTranslator;
import com.redhat.lightblue.crud.ldap.translator.unboundid.SortTranslator;
import com.redhat.lightblue.eval.FieldAccessRoleEvaluator;
import com.redhat.lightblue.eval.Projector;
import com.redhat.lightblue.hystrix.ldap.InsertCommand;
import com.redhat.lightblue.metadata.DataStore;
import com.redhat.lightblue.metadata.EntityMetadata;
import com.redhat.lightblue.metadata.FieldCursor;
import com.redhat.lightblue.metadata.MetadataListener;
import com.redhat.lightblue.query.Projection;
import com.redhat.lightblue.query.QueryExpression;
import com.redhat.lightblue.query.Sort;
import com.redhat.lightblue.query.UpdateExpression;
import com.redhat.lightblue.util.JsonDoc;
import com.redhat.lightblue.util.Path;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPResult;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.controls.ServerSideSortRequestControl;
import com.unboundid.ldap.sdk.controls.VirtualListViewRequestControl;

/**
 * {@link CRUDController} implementation for LDAP.
 *
 * @author dcrissman
 */
public class LdapCRUDController implements CRUDController{

    private final DBResolver dbResolver;

    public LdapCRUDController(DBResolver dbResolver){
        this.dbResolver = dbResolver;
    }

    public CRUDInsertionResponse insert(CRUDOperationContext ctx,
            Projection projection) {
        CRUDInsertionResponse response = new CRUDInsertionResponse();
        response.setNumInserted(0);

        List<DocCtx> documents = ctx.getDocumentsWithoutErrors();
        if (documents == null || documents.isEmpty()) {
            return response;
        }

        EntityMetadata md = ctx.getEntityMetadata(ctx.getEntityName());
        LdapDataStore store = getLdapDataStore(md);

        //TODO Revisit Projection
        //FieldAccessRoleEvaluator roleEval = new FieldAccessRoleEvaluator(md, ctx.getCallerRoles());
        /*Projection combinedProjection = Projection.add(
                projection,
                roleEval.getExcludedFields(FieldAccessRoleEvaluator.Operation.insert));*/

        /*        Projector projector = null;
        if(combinedProjection != null){
            projector = Projector.getInstance(combinedProjection, md);
        }*/

        try {
            LDAPConnection connection = dbResolver.get(store);

            for(DocCtx document : documents){
                //document.setOriginalDocument(document);
                JsonNode rootNode = document.getRoot();

                JsonNode uniqueNode = rootNode.get(store.getUniqueField());
                if(uniqueNode == null){
                    throw new IllegalArgumentException(store.getUniqueField() + " is a required field");
                }

                Entry entry = new Entry(createDN(store, uniqueNode.asText()));

                Iterator<Map.Entry<String, JsonNode>> nodeIterator = rootNode.fields();
                while(nodeIterator.hasNext()){
                    Map.Entry<String, JsonNode> node = nodeIterator.next();
                    if("dn".equalsIgnoreCase(node.getKey())){
                        throw new IllegalArgumentException(
                                "DN should not be included as it's value will be derived from the metadata.basedn and" +
                                " the metadata.uniqueattr. Including the DN as an insert attribute is confusing.");
                    }

                    JsonNode valueNode = node.getValue();
                    if(valueNode.isArray()){
                        List<String> values = new ArrayList<String>();
                        for(JsonNode string : valueNode){
                            values.add(string.asText());
                        }
                        entry.addAttribute(new Attribute(node.getKey(), values));
                    }
                    else{
                        if(node.getKey().endsWith("#") || node.getKey().equalsIgnoreCase("objectType")){
                            //TODO: Indicates the field is an auto-generated array count. Skip for now. See PredefinedFields
                            continue;
                        }

                        entry.addAttribute(new Attribute(node.getKey(), node.getValue().asText()));
                    }
                }

                InsertCommand command = new InsertCommand(connection, entry);

                LDAPResult result = command.execute();
                if(result.getResultCode() != ResultCode.SUCCESS){
                    //TODO: Do something to indicate unsuccessful status
                    continue;
                }

                /*if(projector != null){
                    JsonDoc jsonDoc = null; //TODO: actually populate field.
                    document.setOutputDocument(projector.project(jsonDoc, ctx.getFactory().getNodeFactory()));
                }
                else{*/
                //document.setOutputDocument(new JsonDoc(new ObjectNode(ctx.getFactory().getNodeFactory())));
                //}

                response.setNumInserted(response.getNumInserted() + 1);
            }
        }
        catch (LDAPException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return response;
    }

    public CRUDSaveResponse save(CRUDOperationContext ctx, boolean upsert,
            Projection projection) {
        // TODO Auto-generated method stub
        return null;
    }

    public CRUDUpdateResponse update(CRUDOperationContext ctx,
            QueryExpression query, UpdateExpression update,
            Projection projection) {
        // TODO Auto-generated method stub
        return null;
    }

    public CRUDDeleteResponse delete(CRUDOperationContext ctx,
            QueryExpression query) {
        // TODO Auto-generated method stub
        return null;
    }

    public CRUDFindResponse find(CRUDOperationContext ctx,
            QueryExpression query, Projection projection, Sort sort, Long from,
            Long to) {

        if (query == null) {
            throw new IllegalArgumentException("No query was provided.");
        }
        if (projection == null) {
            throw new IllegalArgumentException("No projection was provided");
        }

        EntityMetadata md = ctx.getEntityMetadata(ctx.getEntityName());
        LdapDataStore store = getLdapDataStore(md);

        CRUDFindResponse response = new CRUDFindResponse();
        response.setSize(0);

        try {
            LDAPConnection connection = dbResolver.get(store);

            //TODO: Support scopes other than SUB
            SearchRequest request = new SearchRequest(
                    store.getBaseDN(),
                    SearchScope.SUB,
                    new FilterTranslator().translate(query),
                    collectRequiredFields(md, projection, query, sort));
            if(sort != null){
                request.addControl(new ServerSideSortRequestControl(true, new SortTranslator().translate(sort)));
            }
            if((from != null) && (from > 0)){
                request.addControl(new VirtualListViewRequestControl(from.intValue(), 0, new Long(to - from).intValue(), 0, null, false));
            }

            SearchResult result = connection.search(request);

            response.setSize(result.getEntryCount());
            ctx.setDocuments(new ResultTranslator(ctx.getFactory().getNodeFactory()).translate(result, md));

            Projector projector = Projector.getInstance(
                    Projection.add(
                            projection,
                            new FieldAccessRoleEvaluator(
                                    md,
                                    ctx.getCallerRoles()).getExcludedFields(FieldAccessRoleEvaluator.Operation.find)
                            ),
                            md);
            for (DocCtx document : ctx.getDocuments()) {
                document.setOutputDocument(projector.project(document, ctx.getFactory().getNodeFactory()));
            }
        }
        catch (LDAPException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return response;
    }

    public void updatePredefinedFields(CRUDOperationContext ctx, JsonDoc doc) {
        // TODO Auto-generated method stub
    }

    public MetadataListener getMetadataListener() {
        return null;
    }

    /**
     * Shortcut method to get and return the {@link LdapDataStore} on the passed in
     * {@link EntityMetadata}.
     * @param md - {@link EntityMetadata}
     * @return {@link LdapDataStore}
     * @throws IllegalArgumentException if an {@link LdapDataStore} is not set
     * on the {@link EntityMetadata}.
     */
    private LdapDataStore getLdapDataStore(EntityMetadata md){
        DataStore store = md.getDataStore();
        if(!(store instanceof LdapDataStore)){
            throw new IllegalArgumentException("DataStore of type " + store.getClass() + " is not supported.");
        }
        return (LdapDataStore) store;
    }

    /**
     * Creates and returns a unique DN.
     * @param store - {@link LdapDataStore} to use as the BaseDN and field that
     * is used to represent uniqueness.
     * @param uniqueValue - value that makes the entity unique.
     * @return a string representation of the DN.
     */
    private String createDN(LdapDataStore store, String uniqueValue){
        return store.getUniqueField() + "=" + uniqueValue + "," + store.getBaseDN();
    }

    /**
     * Returns a list of the field names that are needed for the operation to be
     * successful.
     * @param md - {@link EntityMetadata}.
     * @param projection - (optional) {@link Projection}.
     * @param query - (optional) {@link QueryExpression}.
     * @param sort - (optional) {@link Sort}.
     * @return list of field names.
     */
    private String[] collectRequiredFields(EntityMetadata md,
            Projection projection, QueryExpression query, Sort sort){
        Set<String> fields = new HashSet<String>();

        FieldCursor cursor = md.getFieldCursor();
        while(cursor.next()) {
            Path node = cursor.getCurrentPath();
            String fieldName = node.getLast();

            if(fieldName.endsWith("#")){
                fields.add(fieldName.substring(0, fieldName.length() - 1));
            }
            else{
                if(((projection != null) && projection.isFieldRequiredToEvaluateProjection(node))
                        || ((query != null) && query.isRequired(node))
                        || ((sort != null) && sort.isRequired(node))) {
                    fields.add(fieldName);
                }
            }
        }

        return fields.toArray(new String[0]);
    }

}
