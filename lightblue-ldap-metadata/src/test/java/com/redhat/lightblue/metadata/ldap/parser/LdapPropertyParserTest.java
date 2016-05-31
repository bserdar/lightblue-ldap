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
package com.redhat.lightblue.metadata.ldap.parser;

import static com.redhat.lightblue.util.JsonUtils.json;
import static com.redhat.lightblue.util.test.AbstractJsonNodeTest.loadJsonNode;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.json.JSONException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.skyscreamer.jsonassert.JSONAssert;

import com.fasterxml.jackson.databind.JsonNode;
import com.redhat.lightblue.common.ldap.LdapConstant;
import com.redhat.lightblue.metadata.ldap.model.LdapMetadata;
import com.redhat.lightblue.test.MetadataUtil;
import com.redhat.lightblue.util.Path;

public class LdapPropertyParserTest {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Test
    public void testParse() throws IOException{
        LdapMetadata ldapMetadata = new LdapPropertyParser<JsonNode>().parseProperty(
                MetadataUtil.createJSONMetadataParser(LdapConstant.BACKEND, null),
                loadJsonNode("./ldap-segment-metadata.json"),
                LdapConstant.BACKEND);

        assertNotNull(ldapMetadata);

        Map<Path, String> fieldsToAttributes = ldapMetadata.getFieldsToAttributes();
        assertNotNull(fieldsToAttributes);
        assertEquals(2, fieldsToAttributes.size());

        Map<Path, String> expected = new HashMap<Path, String>();
        expected.put(new Path("firstName"), "givenName");
        expected.put(new Path("lastName"), "sn");

        //assert the two maps are identical
        assertEquals(fieldsToAttributes.size(), expected.size());
        assertThat(new HashSet<Object>(fieldsToAttributes.entrySet()), hasItems(expected.entrySet().toArray()));
    }

    @Test
    public void testParse_NoProperties() throws IOException{
        LdapMetadata ldapMetadata = new LdapPropertyParser<JsonNode>().parseProperty(
                MetadataUtil.createJSONMetadataParser(LdapConstant.BACKEND, null),
                json("{\"ldap\": {}}"),
                LdapConstant.BACKEND);

        assertNotNull(ldapMetadata);

        assertTrue(ldapMetadata.getFieldsToAttributes().isEmpty());
    }

    @Test
    public void testParse_IncorrectBackend(){
        expectedEx.expect(com.redhat.lightblue.util.Error.class);
        expectedEx.expectMessage("{\"objectType\":\"error\",\"errorCode\":\"metadata:IllFormedMetadata\",\"msg\":\"fakebackend\"}");

        new LdapPropertyParser<JsonNode>().parseProperty(null, null, "fakebackend");
    }

    @Test
    public void testConvert() throws IOException, JSONException{
        LdapMetadata ldapMetadata = new LdapMetadata();
        ldapMetadata.addFieldToAttribute(new Path("firstName"), "givenName");
        ldapMetadata.addFieldToAttribute(new Path("lastName"), "sn");

        JsonNode node = json("{}");

        new LdapPropertyParser<JsonNode>().convertProperty(
                MetadataUtil.createJSONMetadataParser(LdapConstant.BACKEND, null),
                node,
                LdapConstant.BACKEND,
                ldapMetadata);

        JSONAssert.assertEquals(
                "{\"ldap\":{\"fieldsToAttributes\":[{\"field\":\"lastName\",\"attribute\":\"sn\"},{\"field\":\"firstName\",\"attribute\":\"givenName\"}]}}",
                node.toString(), false);
    }

    @Test
    public void testConvert_NoMappings() throws IOException, JSONException{
        LdapMetadata ldapMetadata = new LdapMetadata();

        JsonNode node = json("{}");

        new LdapPropertyParser<JsonNode>().convertProperty(
                MetadataUtil.createJSONMetadataParser(LdapConstant.BACKEND, null),
                node,
                LdapConstant.BACKEND,
                ldapMetadata);

        JSONAssert.assertEquals("{\"ldap\":{}}",
                node.toString(), true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvert_invalidObject(){
        new LdapPropertyParser<JsonNode>().convertProperty(null, null,
                LdapConstant.BACKEND,
                new Object());
    }

}
