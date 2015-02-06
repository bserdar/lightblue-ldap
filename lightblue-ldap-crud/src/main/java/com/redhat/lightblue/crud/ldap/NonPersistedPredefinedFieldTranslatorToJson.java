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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redhat.lightblue.common.ldap.LightblueUtil;
import com.redhat.lightblue.metadata.ArrayField;
import com.redhat.lightblue.metadata.EntityMetadata;
import com.redhat.lightblue.metadata.FieldCursor;
import com.redhat.lightblue.metadata.FieldTreeNode;
import com.redhat.lightblue.metadata.Fields;
import com.redhat.lightblue.metadata.ObjectField;
import com.redhat.lightblue.metadata.types.IntegerType;
import com.redhat.lightblue.metadata.types.StringType;
import com.redhat.lightblue.util.Path;

public abstract class NonPersistedPredefinedFieldTranslatorToJson<S> extends TranslatorToJson<S>{

    /** Holding bin for the currently relevant {@link Fields}. */
    private Fields currentFields;
    private ObjectNode currentTargetObjectNode;

    public NonPersistedPredefinedFieldTranslatorToJson(JsonNodeFactory factory,
            EntityMetadata entityMetadata) {
        super(factory, entityMetadata);
        currentFields = entityMetadata.getFields();
    }

    @Override
    protected void appendToJsonNode(S source, ObjectNode targetNode, FieldCursor fieldCursor){
        FieldTreeNode field = fieldCursor.getCurrentNode();

        if(LightblueUtil.isFieldAnArrayCount(field.getName(), currentFields)){
            /*
             * This case will be handled by the array itself, allowing this to
             * process runs the risk of nulling out the correct value.
             */
            return;
        }

        Path fieldPath = fieldCursor.getCurrentPath();
        currentTargetObjectNode = targetNode;

        if(LightblueUtil.isFieldObjectType(fieldPath.toString())){
            targetNode.set(fieldPath.toString(), toJson(StringType.TYPE, entityMetadata.getEntityInfo().getName()));
        }
        else{
            super.appendToJsonNode(source, targetNode, fieldCursor);
        }

    }

    @Override
    protected JsonNode translate(ArrayField field, Object o, FieldCursor fieldCursor){
        currentTargetObjectNode.set(
                LightblueUtil.createArrayCountFieldName(field.getName()),
                toJson(IntegerType.TYPE, getSizeOf(o)));
        return super.translate(field, o, fieldCursor);
    }

    @Override
    protected JsonNode translate(S source, ObjectField field, FieldCursor fieldCursor){
        //Store the current fields so that they can be put back after this operation is complete.
        Fields storeFieldsUntilLater = currentFields;
        currentFields = field.getFields();

        JsonNode node = super.translate(source, field, fieldCursor);

        currentFields = storeFieldsUntilLater;

        return node;
    }

    protected abstract int getSizeOf(Object o);

}
