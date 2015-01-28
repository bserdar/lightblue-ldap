/*
 Copyright 2015 Red Hat, Inc. and/or its affiliates.

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
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.redhat.lightblue.common.ldap.LdapConstant;
import com.redhat.lightblue.common.ldap.LdapFieldNameTranslator;
import com.redhat.lightblue.common.ldap.LightblueUtil;
import com.redhat.lightblue.metadata.ArrayElement;
import com.redhat.lightblue.metadata.ArrayField;
import com.redhat.lightblue.metadata.EntityMetadata;
import com.redhat.lightblue.metadata.SimpleField;
import com.redhat.lightblue.metadata.Type;
import com.redhat.lightblue.metadata.types.BinaryType;
import com.redhat.lightblue.metadata.types.DateType;
import com.redhat.lightblue.util.Error;
import com.redhat.lightblue.util.JsonDoc;
import com.redhat.lightblue.util.JsonNodeCursor;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.util.StaticUtils;

/**
 * Builds populated instances of {@link Entry} for LDAP interaction.
 *
 * @author dcrissman
 */
public class EntryBuilder extends TranslatorFromJson<Entry>{

    private final LdapFieldNameTranslator fieldNameTranslator;

    public EntryBuilder(EntityMetadata md, LdapFieldNameTranslator fieldNameTranslator){
        super(md);
        this.fieldNameTranslator = fieldNameTranslator;
    }

    public Entry build(String dn, JsonDoc document){
        Error.push(LdapConstant.ATTRIBUTE_DN + "=" + dn);
        Entry entry = new Entry(dn);
        translate(document, entry);
        Error.pop();
        return entry;
    }

    @Override
    protected Object fromJson(Type type, JsonNode node){
        if(type instanceof DateType){
            return StaticUtils.encodeGeneralizedTime((Date)type.fromJson(node));
        }
        else if(type instanceof BinaryType){
            return type.fromJson(node);
        }
        else{
            return super.fromJson(type, node).toString();
        }
    }

    @Override
    protected void translate(SimpleField field, JsonNode node, Entry target) {
        String attributeName = fieldNameTranslator.translateFieldName(field.getFullPath());

        if(LdapConstant.ATTRIBUTE_DN.equalsIgnoreCase(attributeName)){
            throw Error.get("Invalid Field Definition", "'" + LdapConstant.ATTRIBUTE_DN + "' should not be included as its value will be"
                    + " derived from the metadata.basedn and"
                    + " the metadata.uniqueattr. Including the '" + LdapConstant.ATTRIBUTE_DN
                    + "' as an insert attribute is confusing.");
        }
        else if(LightblueUtil.isFieldObjectType(attributeName)
                || LightblueUtil.isFieldAnArrayCount(attributeName, getEntityMetadata().getFields())){
            /*
             * Indicates the field is auto-generated for lightblue purposes. These fields
             * should not be inserted into LDAP.
             */
            return;
        }

        Type type = field.getType();
        Object o = fromJson(type, node);
        if(type instanceof BinaryType) {
            target.addAttribute(attributeName, (byte[])o);
        }
        else{
            target.addAttribute(attributeName, o.toString());
        }
    }

    @Override
    protected void translateSimpleArray(ArrayField field, List<Object> items, Entry target) {
        ArrayElement arrayElement = field.getElement();
        Type arrayElementType = arrayElement.getType();
        String attributeName = fieldNameTranslator.translateFieldName(field.getFullPath());

        if(arrayElementType instanceof BinaryType){
            List<byte[]> bytes = new ArrayList<byte[]>();
            for(Object item : items){
                bytes.add((byte[])item);
            }
            target.addAttribute(attributeName, bytes.toArray(new byte[0][]));
        }
        else{
            List<String> values = new ArrayList<String>();
            for(Object item : items){
                values.add(item.toString());
            }
            target.addAttribute(attributeName, values);
        }
    }

    @Override
    protected void translateObjectArray(ArrayField field, JsonNodeCursor cursor, Entry target) {
        throw Error.get("Unsupported Feature: object array", field.getFullPath().toString());
    }

}
