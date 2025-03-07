/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ANY_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTO_START;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BOOT_TIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEFAULT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DIRECTORY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_DEFAULTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAMES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_REQUIRES_RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PLATFORM_MBEAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROCESS_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.QUERY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD_REQUIRED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ON_RUNTIME_FAILURE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SELECT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WHERE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateFailedResponse;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;
import static org.jboss.as.test.integration.domain.management.util.DomainTestUtils.checkServerState;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.SnapshotDeleteHandler;
import org.jboss.as.controller.operations.common.SnapshotListHandler;
import org.jboss.as.controller.operations.common.SnapshotTakeHandler;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.dmr.ValueExpression;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test of various management operations involving core resources like system properties, paths, interfaces, socket bindings.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class CoreResourceManagementTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    private static final String TEST = "test";
    private static final String BOOT_PROPERTY_NAME = "boot-test";
    private static final String BOOT_PROPERTY_VALUE = "domain";
    private static final Map<String, String> BOOT_TEST_PROPERTIES = Collections.singletonMap(BOOT_PROPERTY_NAME, BOOT_PROPERTY_VALUE);
    private static final ModelNode ROOT_PROP_ADDRESS = new ModelNode();
    private static final ModelNode SERVER_GROUP_PROP_ADDRESS = new ModelNode();
    private static final ModelNode HOST_PROP_ADDRESS = new ModelNode();
    private static final ModelNode HOST_COMPOSITE_PROP_ADDRESS = new ModelNode();
    private static final ModelNode HOST_CLASSLOADING_ADDRESS = new ModelNode();
    private static final ModelNode SERVER_PROP_ADDRESS = new ModelNode();
    private static final ModelNode MAIN_RUNNING_SERVER_ADDRESS = new ModelNode();
    private static final ModelNode MAIN_RUNNING_SERVER_PROP_ADDRESS = new ModelNode();
    private static final PathAddress MAIN_RUNNING_SERVER_CONFIG_ADDRESS = PathAddress.pathAddress(HOST, "primary").append(SERVER_CONFIG, "main-one");
    private static final ModelNode MAIN_RUNNING_SERVER_CLASSLOADING_ADDRESS = new ModelNode();
    private static final ModelNode OTHER_RUNNING_SERVER_ADDRESS = new ModelNode();
    private static final ModelNode OTHER_RUNNING_SERVER_PROP_ADDRESS = new ModelNode();
    private static final ModelNode OTHER_RUNNING_SERVER_CLASSLOADING_ADDRESS = new ModelNode();

    private static final String fileSeparator = System.getProperty("file.separator");

    private static int workerName = 1;

    static {
        ROOT_PROP_ADDRESS.add(SYSTEM_PROPERTY, TEST);
        ROOT_PROP_ADDRESS.protect();
        SERVER_GROUP_PROP_ADDRESS.add(SERVER_GROUP, "other-server-group");
        SERVER_GROUP_PROP_ADDRESS.add(SYSTEM_PROPERTY, TEST);
        SERVER_GROUP_PROP_ADDRESS.protect();
        HOST_PROP_ADDRESS.add(HOST, "secondary");
        HOST_PROP_ADDRESS.add(SYSTEM_PROPERTY, TEST);
        HOST_PROP_ADDRESS.protect();
        HOST_COMPOSITE_PROP_ADDRESS.add(HOST, "secondary");
        HOST_COMPOSITE_PROP_ADDRESS.add(SYSTEM_PROPERTY, COMPOSITE);
        HOST_COMPOSITE_PROP_ADDRESS.protect();
        HOST_CLASSLOADING_ADDRESS.add(HOST, "secondary");
        HOST_CLASSLOADING_ADDRESS.add(CORE_SERVICE, PLATFORM_MBEAN);
        HOST_CLASSLOADING_ADDRESS.add(TYPE, "class-loading");
        HOST_CLASSLOADING_ADDRESS.protect();
        SERVER_PROP_ADDRESS.add(HOST, "secondary");
        SERVER_PROP_ADDRESS.add(SERVER_CONFIG, "other-two");
        SERVER_PROP_ADDRESS.add(SYSTEM_PROPERTY, TEST);
        SERVER_PROP_ADDRESS.protect();
        MAIN_RUNNING_SERVER_ADDRESS.add(HOST, "primary");
        MAIN_RUNNING_SERVER_ADDRESS.add(SERVER, "main-one");
        MAIN_RUNNING_SERVER_ADDRESS.protect();
        MAIN_RUNNING_SERVER_PROP_ADDRESS.add(HOST, "primary");
        MAIN_RUNNING_SERVER_PROP_ADDRESS.add(SERVER, "main-one");
        MAIN_RUNNING_SERVER_PROP_ADDRESS.add(SYSTEM_PROPERTY, TEST);
        MAIN_RUNNING_SERVER_PROP_ADDRESS.protect();
        MAIN_RUNNING_SERVER_CLASSLOADING_ADDRESS.add(HOST, "primary");
        MAIN_RUNNING_SERVER_CLASSLOADING_ADDRESS.add(SERVER, "main-one");
        MAIN_RUNNING_SERVER_CLASSLOADING_ADDRESS.add(CORE_SERVICE, PLATFORM_MBEAN);
        MAIN_RUNNING_SERVER_CLASSLOADING_ADDRESS.add(TYPE, "class-loading");
        MAIN_RUNNING_SERVER_CLASSLOADING_ADDRESS.protect();
        OTHER_RUNNING_SERVER_ADDRESS.add(HOST, "secondary");
        OTHER_RUNNING_SERVER_ADDRESS.add(SERVER, "other-two");
        OTHER_RUNNING_SERVER_ADDRESS.protect();
        OTHER_RUNNING_SERVER_PROP_ADDRESS.add(HOST, "secondary");
        OTHER_RUNNING_SERVER_PROP_ADDRESS.add(SERVER, "other-two");
        OTHER_RUNNING_SERVER_PROP_ADDRESS.add(SYSTEM_PROPERTY, TEST);
        OTHER_RUNNING_SERVER_PROP_ADDRESS.protect();
        OTHER_RUNNING_SERVER_CLASSLOADING_ADDRESS.add(HOST, "secondary");
        OTHER_RUNNING_SERVER_CLASSLOADING_ADDRESS.add(SERVER, "other-two");
        OTHER_RUNNING_SERVER_CLASSLOADING_ADDRESS.add(CORE_SERVICE, PLATFORM_MBEAN);
        OTHER_RUNNING_SERVER_CLASSLOADING_ADDRESS.add(TYPE, "class-loading");
        OTHER_RUNNING_SERVER_CLASSLOADING_ADDRESS.protect();
    }

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(CoreResourceManagementTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testSystemPropertyManagement() throws IOException {
        DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        DomainClient secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();

        ModelNode response = primaryClient.execute(getReadChildrenNamesOperation(MAIN_RUNNING_SERVER_ADDRESS, SYSTEM_PROPERTY));
        ModelNode returnVal = validateResponse(response);
        int origPropCount = returnVal.asInt();

        ModelNode request = getSystemPropertyAddOperation(ROOT_PROP_ADDRESS, "domain", Boolean.FALSE);
        response = primaryClient.execute(request);
        validateResponse(response);

        // TODO validate response structure

        response = primaryClient.execute(getReadChildrenNamesOperation(MAIN_RUNNING_SERVER_ADDRESS, SYSTEM_PROPERTY));
        returnVal = validateResponse(response);
        Assert.assertEquals(origPropCount + 1, returnVal.asList().size());

        response = primaryClient.execute(getReadChildrenNamesOperation(OTHER_RUNNING_SERVER_ADDRESS, SYSTEM_PROPERTY));
        returnVal = validateResponse(response);
        Assert.assertEquals(origPropCount + 1, returnVal.asList().size());

        response = primaryClient.execute(getReadAttributeOperation(MAIN_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("domain", returnVal.asString());

        response = primaryClient.execute(getReadAttributeOperation(OTHER_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("domain", returnVal.asString());

        // Override at server-group
        request = getSystemPropertyAddOperation(SERVER_GROUP_PROP_ADDRESS, "group", Boolean.FALSE);
        response = primaryClient.execute(request);
        validateResponse(response);

        response = primaryClient.execute(getReadAttributeOperation(MAIN_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("domain", returnVal.asString());

        response = primaryClient.execute(getReadAttributeOperation(OTHER_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("group", returnVal.asString());

        // Change the domain level value, confirm it does not replace override
        request = getWriteAttributeOperation(ROOT_PROP_ADDRESS, "domain2");
        response = primaryClient.execute(request);
        validateResponse(response);

        response = primaryClient.execute(getReadAttributeOperation(MAIN_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("domain2", returnVal.asString());

        response = primaryClient.execute(getReadAttributeOperation(OTHER_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("group", returnVal.asString());

        // Override at the host level
        request = getSystemPropertyAddOperation(HOST_PROP_ADDRESS, "host", Boolean.FALSE);
        response = primaryClient.execute(request);
        validateResponse(response);

        response = primaryClient.execute(getReadAttributeOperation(MAIN_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("domain2", returnVal.asString());

        response = primaryClient.execute(getReadAttributeOperation(OTHER_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("host", returnVal.asString());

        // Change the domain level value, confirm it does not replace override
        request = getWriteAttributeOperation(ROOT_PROP_ADDRESS, "domain3");
        response = primaryClient.execute(request);
        validateResponse(response);

        response = primaryClient.execute(getReadAttributeOperation(MAIN_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("domain3", returnVal.asString());

        response = primaryClient.execute(getReadAttributeOperation(OTHER_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("host", returnVal.asString());

        // Change the server group level value, confirm it does not replace override
        request = getWriteAttributeOperation(SERVER_GROUP_PROP_ADDRESS, "group2");
        response = primaryClient.execute(request);
        validateResponse(response);

        response = primaryClient.execute(getReadAttributeOperation(MAIN_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("domain3", returnVal.asString());

        response = primaryClient.execute(getReadAttributeOperation(OTHER_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("host", returnVal.asString());

        // Override at the server level
        request = getSystemPropertyAddOperation(SERVER_PROP_ADDRESS, "server", Boolean.FALSE);
        response = primaryClient.execute(request);
        validateResponse(response);

        response = primaryClient.execute(getReadAttributeOperation(MAIN_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("domain3", returnVal.asString());

        response = primaryClient.execute(getReadAttributeOperation(OTHER_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("server", returnVal.asString());

        // Change the server group level value, confirm it does not replace override
        request = getWriteAttributeOperation(HOST_PROP_ADDRESS, "host2");
        response = primaryClient.execute(request);
        validateResponse(response);

        response = primaryClient.execute(getReadAttributeOperation(MAIN_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("domain3", returnVal.asString());

        response = primaryClient.execute(getReadAttributeOperation(OTHER_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("server", returnVal.asString());

        // Modify the server level value
        request = getWriteAttributeOperation(SERVER_PROP_ADDRESS, "server1");
        response = secondaryClient.execute(request);   // Start using the secondary client
        validateResponse(response);

        response = primaryClient.execute(getReadAttributeOperation(MAIN_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("domain3", returnVal.asString());

        response = secondaryClient.execute(getReadAttributeOperation(OTHER_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("server1", returnVal.asString());

        // Remove from top down
        request = getEmptyOperation(REMOVE, ROOT_PROP_ADDRESS);
        response = primaryClient.execute(request);
        validateResponse(response);

        response = primaryClient.execute(getReadChildrenNamesOperation(MAIN_RUNNING_SERVER_ADDRESS, SYSTEM_PROPERTY));
        returnVal = validateResponse(response);
        Assert.assertEquals(origPropCount, returnVal.asList().size());

        response = secondaryClient.execute(getReadAttributeOperation(OTHER_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("server1", returnVal.asString());

        request = getEmptyOperation(REMOVE, SERVER_GROUP_PROP_ADDRESS);
        response = primaryClient.execute(request);
        validateResponse(response);

        response = primaryClient.execute(getReadChildrenNamesOperation(MAIN_RUNNING_SERVER_ADDRESS, SYSTEM_PROPERTY));
        returnVal = validateResponse(response);
        Assert.assertEquals(origPropCount, returnVal.asList().size());

        response = secondaryClient.execute(getReadAttributeOperation(OTHER_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("server1", returnVal.asString());

        request = getEmptyOperation(REMOVE, HOST_PROP_ADDRESS);
        response = primaryClient.execute(request);
        validateResponse(response);

        response = primaryClient.execute(getReadChildrenNamesOperation(MAIN_RUNNING_SERVER_ADDRESS, SYSTEM_PROPERTY));
        returnVal = validateResponse(response);
        Assert.assertEquals(origPropCount, returnVal.asList().size());

        response = secondaryClient.execute(getReadAttributeOperation(OTHER_RUNNING_SERVER_PROP_ADDRESS, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("server1", returnVal.asString());

        request = getEmptyOperation(REMOVE, SERVER_PROP_ADDRESS);
        response = primaryClient.execute(request);
        validateResponse(response);

        response = primaryClient.execute(getReadChildrenNamesOperation(MAIN_RUNNING_SERVER_ADDRESS, SYSTEM_PROPERTY));
        returnVal = validateResponse(response);
        Assert.assertEquals(origPropCount, returnVal.asList().size());

        response = secondaryClient.execute(getReadChildrenNamesOperation(OTHER_RUNNING_SERVER_ADDRESS, SYSTEM_PROPERTY));
        returnVal = validateResponse(response);
        Assert.assertEquals(origPropCount, returnVal.asList().size());
    }

    @Test //Covers WFCORE-499
    public void testSystemPropertyBootTime() throws IOException, MgmtOperationException {
        DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode propertyAddress = MAIN_RUNNING_SERVER_CONFIG_ADDRESS.toModelNode().add(SYSTEM_PROPERTY, BOOT_PROPERTY_NAME);
        validateBootProperty(primaryClient, propertyAddress);
        propertyAddress = new ModelNode().add(SERVER_GROUP, "main-server-group").add(SYSTEM_PROPERTY, BOOT_PROPERTY_NAME);
        validateBootProperty(primaryClient, propertyAddress);
        propertyAddress = new ModelNode().add(HOST, "primary").add(SYSTEM_PROPERTY, BOOT_PROPERTY_NAME);
        validateBootProperty(primaryClient, propertyAddress);
        propertyAddress = new ModelNode().add(SYSTEM_PROPERTY, BOOT_PROPERTY_NAME);
        validateBootProperty(primaryClient, propertyAddress);
    }

    private void validateBootProperty(DomainClient primaryClient, ModelNode propertyAddress) throws IOException, MgmtOperationException {
        ModelNode response = primaryClient.execute(getReadChildrenNamesOperation(MAIN_RUNNING_SERVER_ADDRESS, SYSTEM_PROPERTY));
        ModelNode returnVal = validateResponse(response);
        int origPropCount = returnVal.asInt();

        ServiceActivatorDeploymentUtil.validateNoProperties(primaryClient, PathAddress.pathAddress(MAIN_RUNNING_SERVER_ADDRESS), BOOT_TEST_PROPERTIES.keySet());

        ModelNode request = getSystemPropertyAddOperation(propertyAddress, BOOT_PROPERTY_VALUE, Boolean.FALSE);
        response = primaryClient.execute(request);
        validateResponse(response);

        validateBootSystemProperty(primaryClient, MAIN_RUNNING_SERVER_ADDRESS, true, origPropCount, BOOT_PROPERTY_VALUE);

        request = getSystemPropertyRemoveOperation(propertyAddress);
        validateResponse(primaryClient.execute(request));
        restartServer(primaryClient, MAIN_RUNNING_SERVER_CONFIG_ADDRESS);

        ServiceActivatorDeploymentUtil.validateNoProperties(primaryClient, PathAddress.pathAddress(MAIN_RUNNING_SERVER_ADDRESS), BOOT_TEST_PROPERTIES.keySet());
        request = getSystemPropertyAddOperation(propertyAddress, BOOT_PROPERTY_VALUE, Boolean.TRUE);
        response = primaryClient.execute(request);
        validateResponse(response);

        restartServer(primaryClient, MAIN_RUNNING_SERVER_CONFIG_ADDRESS);
        // TODO validate response structure
        validateBootSystemProperty(primaryClient, MAIN_RUNNING_SERVER_ADDRESS, true, origPropCount, BOOT_PROPERTY_VALUE);

        request = getSystemPropertyRemoveOperation(propertyAddress);
        validateResponse(primaryClient.execute(request));
        restartServer(primaryClient, MAIN_RUNNING_SERVER_CONFIG_ADDRESS);
    }

    /** Test for AS7-3443 */
    @Test
    public void testSystemPropertyComposites() throws Exception {

        DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        DomainClient secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();

        ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        ModelNode steps = composite.get(STEPS);
        steps.add(getSystemPropertyAddOperation(HOST_COMPOSITE_PROP_ADDRESS, "host", null));
        steps.add(getWriteAttributeOperation(HOST_COMPOSITE_PROP_ADDRESS, "host2"));

        ModelNode response = primaryClient.execute(composite);
        validateResponse(response);

        primaryClient.execute(getEmptyOperation(REMOVE, HOST_COMPOSITE_PROP_ADDRESS));

        response = secondaryClient.execute(composite);
        validateResponse(response);
    }

    @Test
    public void testSystemPropertyExpressions() throws Exception {
        DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        DomainClient secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();

        //Make sure that the domain.xml system properties can be resolved on the servers
        final String propOne = "jboss.domain.test.property.one";
        final String propTwo = "jboss.domain.test.property.two";
        final String propThree = "jboss.domain.test.property.three";
        final ModelNode rootOneAddr = getPropertyAddress(ROOT_PROP_ADDRESS, propOne);
        final ModelNode rootTwoAddr = getPropertyAddress(ROOT_PROP_ADDRESS, propTwo);
        final ModelNode mainServerOne = getPropertyAddress(MAIN_RUNNING_SERVER_PROP_ADDRESS, propOne);
        final ModelNode mainServerTwo = getPropertyAddress(MAIN_RUNNING_SERVER_PROP_ADDRESS, propTwo);
        final ModelNode otherServerOne = getPropertyAddress(OTHER_RUNNING_SERVER_PROP_ADDRESS, propOne);
        final ModelNode otherServerTwo = getPropertyAddress(OTHER_RUNNING_SERVER_PROP_ADDRESS, propTwo);

        //Check the raw values
        ModelNode response = primaryClient.execute(getReadAttributeOperation(rootOneAddr, VALUE));
        ModelNode returnVal = validateResponse(response);
        Assert.assertEquals("ONE", returnVal.asString());

        response = primaryClient.execute(getReadAttributeOperation(rootTwoAddr, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals(ModelType.EXPRESSION, returnVal.getType());
        Assert.assertEquals("${jboss.domain.test.property.one}", returnVal.asString());

        response = primaryClient.execute(getReadAttributeOperation(mainServerOne, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("ONE", returnVal.asString());

        response = primaryClient.execute(getReadAttributeOperation(mainServerTwo, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals(ModelType.EXPRESSION, returnVal.getType());
        Assert.assertEquals("${jboss.domain.test.property.one}", returnVal.asString());

        response = secondaryClient.execute(getReadAttributeOperation(otherServerOne, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals("ONE", returnVal.asString());

        response = secondaryClient.execute(getReadAttributeOperation(otherServerTwo, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals(ModelType.EXPRESSION, returnVal.getType());
        Assert.assertEquals("${jboss.domain.test.property.one}", returnVal.asString());

        //Resolve the system properties
        response = primaryClient.execute(getResolveExpressionOperation(propTwo, MAIN_RUNNING_SERVER_ADDRESS));
        returnVal = validateResponse(response);
        Assert.assertEquals("ONE", returnVal.asString());

        response = secondaryClient.execute(getResolveExpressionOperation(propTwo, OTHER_RUNNING_SERVER_ADDRESS));
        returnVal = validateResponse(response);
        Assert.assertEquals("ONE", returnVal.asString());

        //Add another system property and check that gets resolved on the servers
        ModelNode addOp = new ModelNode();
        addOp.get(OP).set(ADD);
        addOp.get(OP_ADDR).add(SYSTEM_PROPERTY, propThree);
        addOp.get(VALUE).set("${jboss.domain.test.property.one}");
        response = primaryClient.execute(addOp);
        validateResponse(response);

        final ModelNode rootThreeAddr = getPropertyAddress(ROOT_PROP_ADDRESS, propThree);
        final ModelNode mainServerThree = getPropertyAddress(MAIN_RUNNING_SERVER_PROP_ADDRESS, propThree);
        final ModelNode otherServerThree = getPropertyAddress(OTHER_RUNNING_SERVER_PROP_ADDRESS, propThree);

        response = primaryClient.execute(getReadAttributeOperation(rootThreeAddr, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals(ModelType.EXPRESSION, returnVal.getType());
        Assert.assertEquals("${jboss.domain.test.property.one}", returnVal.asString());

        response = primaryClient.execute(getReadAttributeOperation(mainServerThree, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals(ModelType.EXPRESSION, returnVal.getType());
        Assert.assertEquals("${jboss.domain.test.property.one}", returnVal.asString());

        response = secondaryClient.execute(getReadAttributeOperation(otherServerThree, VALUE));
        returnVal = validateResponse(response);
        Assert.assertEquals(ModelType.EXPRESSION, returnVal.getType());
        Assert.assertEquals("${jboss.domain.test.property.one}", returnVal.asString());

        response = primaryClient.execute(getResolveExpressionOperation(propThree, MAIN_RUNNING_SERVER_ADDRESS));
        returnVal = validateResponse(response);
        Assert.assertEquals("ONE", returnVal.asString());

        response = secondaryClient.execute(getResolveExpressionOperation(propThree, OTHER_RUNNING_SERVER_ADDRESS));
        returnVal = validateResponse(response);
        Assert.assertEquals("ONE", returnVal.asString());
    }

    @Test
    public void testPlatformMBeanManagement() throws Exception {

        // Just validate that the resources exist at the expected location
        DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        ModelNode response = primaryClient.execute(getReadAttributeOperation(HOST_CLASSLOADING_ADDRESS, "loaded-class-count"));
        ModelNode returnVal = validateResponse(response);
        Assert.assertEquals(ModelType.INT, returnVal.getType());

        response = primaryClient.execute(getReadAttributeOperation(MAIN_RUNNING_SERVER_CLASSLOADING_ADDRESS, "loaded-class-count"));
        returnVal = validateResponse(response);
        Assert.assertEquals(ModelType.INT, returnVal.getType());

        response = primaryClient.execute(getReadAttributeOperation(OTHER_RUNNING_SERVER_CLASSLOADING_ADDRESS, "loaded-class-count"));
        returnVal = validateResponse(response);
        Assert.assertEquals(ModelType.INT, returnVal.getType());

    }

    @Test
    public void testUndefineSocketBindingPortOffset() throws IOException {
        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        final ModelNode address = new ModelNode();
        address.add("server-group", "other-server-group");
        address.protect();
        {
            final ModelNode operation = new ModelNode();
            operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
            operation.get(OP_ADDR).set(address);
            operation.get(NAME).set("socket-binding-port-offset");
            operation.get(INCLUDE_DEFAULTS).set(false);

            final ModelNode response = primaryClient.execute(operation);
            validateResponse(response);
            Assert.assertFalse(response.get(RESULT).isDefined());
        }
        {
            final ModelNode operation = new ModelNode();
            operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
            operation.get(OP_ADDR).set(address);
            operation.get(NAME).set("socket-binding-port-offset");
            operation.get(VALUE).set(0);

            final ModelNode response = primaryClient.execute(operation);
            validateResponse(response);
        }
        {
            final ModelNode operation = new ModelNode();
            operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
            operation.get(OP_ADDR).set(address);
            operation.get(NAME).set("socket-binding-port-offset");
            operation.get(INCLUDE_DEFAULTS).set(false);

            final ModelNode response = primaryClient.execute(operation);
            validateResponse(response);
            Assert.assertTrue(response.get(RESULT).isDefined());
            Assert.assertEquals(0, response.get(RESULT).asInt());
        }
        {
            final ModelNode operation = new ModelNode();
            operation.get(OP).set(UNDEFINE_ATTRIBUTE_OPERATION);
            operation.get(OP_ADDR).set(address);
            operation.get(NAME).set("socket-binding-port-offset");

            final ModelNode response = primaryClient.execute(operation);
            validateResponse(response);
        }
        {
            final ModelNode operation = new ModelNode();
            operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
            operation.get(OP_ADDR).set(address);
            operation.get(NAME).set("socket-binding-port-offset");
            operation.get(INCLUDE_DEFAULTS).set(false);

            final ModelNode response = primaryClient.execute(operation);
            validateResponse(response);
            Assert.assertFalse(response.get(RESULT).isDefined());
        }
    }

    @Test
    public void testDomainSnapshot() throws Exception {
        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        ModelNode snapshotOperation = new ModelNode();
        snapshotOperation.get(OP).set(SnapshotTakeHandler.DEFINITION.getName());
        snapshotOperation.get(OP_ADDR).setEmptyList();
        final String snapshot = validateResponse(primaryClient.execute(snapshotOperation)).asString();
        Assert.assertNotNull(snapshot);
        Assert.assertFalse(snapshot.isEmpty());

        ModelNode listSnapshotOperation = new ModelNode();
        listSnapshotOperation.get(OP).set(SnapshotListHandler.DEFINITION.getName());
        listSnapshotOperation.get(OP_ADDR).setEmptyList();
        ModelNode listResult = validateResponse(primaryClient.execute(listSnapshotOperation));
        Set<String> snapshots = new HashSet<String>();
        for (ModelNode curr : listResult.get(NAMES).asList()) {
            snapshots.add(listResult.get(DIRECTORY).asString() + fileSeparator + curr.asString());
        }

        Assert.assertTrue(snapshots.contains(snapshot));

        ModelNode deleteSnapshotOperation = new ModelNode();
        deleteSnapshotOperation.get(OP).set(SnapshotDeleteHandler.DEFINITION.getName());
        deleteSnapshotOperation.get(OP_ADDR).setEmptyList();
        deleteSnapshotOperation.get(NAME).set(snapshot.substring(snapshot.lastIndexOf(fileSeparator)  + fileSeparator.length()));
        validateResponse(primaryClient.execute(deleteSnapshotOperation), false);

        listResult = validateResponse(primaryClient.execute(listSnapshotOperation));
        snapshots = new HashSet<String>();
        for (ModelNode curr : listResult.get(NAMES).asList()) {
            snapshots.add(listResult.get(DIRECTORY).asString() + fileSeparator + curr.asString());
        }

        Assert.assertFalse(snapshots.contains(snapshot));
    }

    @Test
    public void testPrimarySnapshot() throws Exception {
        testSnapshot(new ModelNode().add(HOST, "primary"));
    }

    @Test
    public void testSecondarySnapshot() throws Exception {
        testSnapshot(new ModelNode().add(HOST, "secondary"));
    }

    @Test
    public void testCannotInvokeManagedServerOperations() throws Exception {
        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        ModelNode serverAddTf = getAddWorkerOperation(
                new ModelNode().add("host", "primary").add("server", "main-one").add("subsystem", "io").add("worker", ("cannot-" + workerName++)));

        ModelNode desc = validateFailedResponse(primaryClient.execute(serverAddTf));
        String errorCode = getNotAuthorizedErrorCode();
        Assert.assertTrue(desc.toString() + " does not contain " + errorCode, desc.toString().contains(errorCode));

        ModelNode secondaryThreeAddress = new ModelNode().add("host", "secondary").add("server", "main-three").add("subsystem", "io").add("worker", ("cannot-" + workerName++));
        serverAddTf = getAddWorkerOperation(secondaryThreeAddress);

        desc = validateFailedResponse(primaryClient.execute(serverAddTf));
        Assert.assertTrue(desc.toString() + " does not contain " + errorCode, desc.toString().contains(errorCode));
    }

    @Test
    public void testCannotInvokeManagedPrimaryServerOperationsInDomainComposite() throws Exception {
        testCannotInvokeManagedServerOperationsComposite(new ModelNode().add("host", "primary").add("server", "main-one").add("subsystem", "io"));
    }

    @Test
    public void testCannotInvokeManagedSecondaryServerOperationsInDomainComposite() throws Exception {
        testCannotInvokeManagedServerOperationsComposite(new ModelNode().add("host", "secondary").add("server", "main-three").add("subsystem", "io"));
    }

    @Test
    public void testCannotInvokeManagedPrimaryServerOperationsInServerComposite() throws Exception {
        testCannotInvokeManagedServerOperationsComposite("primary", "main-one", new ModelNode().add("subsystem", "io"));
    }

    @Test
    public void testCannotInvokeManagedSecondaryServerOperationsInServerComposite() throws Exception {
        testCannotInvokeManagedServerOperationsComposite("secondary", "main-three", new ModelNode().add("subsystem", "io"));
    }

    @Test
    public void testReadSystemPropertyResourceOnServerFromComposite() throws Exception {
        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        ModelNode composite = new ModelNode();
        composite.get(OP).set(CompositeOperationHandler.NAME);
        composite.get(OP_ADDR).setEmptyList();
        composite.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);

        ModelNode server1 = new ModelNode();
        server1.get(OP).set(READ_RESOURCE_OPERATION);
        server1.get(OP_ADDR).add("host", "primary").add("server", "main-one");
        ModelNode server3 = new ModelNode();
        server3.get(OP).set(READ_RESOURCE_OPERATION);
        server3.get(OP_ADDR).add("host", "secondary").add("server", "main-three");
        composite.get(STEPS).add(server1);
        composite.get(STEPS).add(server3);

        ModelNode result = primaryClient.execute(composite);
        validateResponse(result);
    }

    @Test
    public void testSetSystemPropertyOnServerFromComposite() throws Exception {
        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        ModelNode composite = new ModelNode();
        composite.get(OP).set(CompositeOperationHandler.NAME);
        composite.get(OP_ADDR).setEmptyList();
        composite.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);

        ModelNode server1 = new ModelNode();
        server1.get(OP).set(ADD);
        server1.get(OP_ADDR).add("host", "primary").add("server", "main-one").add("system-property", "domain-test-property");
        ModelNode server3 = new ModelNode();
        server3.get(OP).set(ADD);
        server3.get(OP_ADDR).add("host", "secondary").add("server", "main-three").add("system-property", "domain-test-property");
        composite.get(STEPS).add(server1);
        composite.get(STEPS).add(server3);

        ModelNode response = primaryClient.execute(composite);
        validateFailedResponse(response);

        String errorCode = getNotAuthorizedErrorCode();
        Assert.assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains(errorCode));
        Assert.assertTrue(response.toString(), response.hasDefined(RESULT, "step-1", FAILURE_DESCRIPTION));
        Assert.assertTrue(response.toString(), response.get(RESULT, "step-1", FAILURE_DESCRIPTION).asString().contains(errorCode));
    }

    /**
     * Test for AS7-3600
     */
    @Test
    public void testAddRemoveHostInterface() throws Exception {
        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        final String ifaceName = "testing-interface";

        ModelNode add = new ModelNode();
        add.get(OP).set(ADD);
        add.get(OP_ADDR).add(HOST, "primary").add(INTERFACE, ifaceName);
        add.get(ANY_ADDRESS).set(true);

        validateResponse(primaryClient.execute(add));

        ModelNode read = new ModelNode();
        read.get(OP).set(READ_RESOURCE_OPERATION);
        read.get(OP_ADDR).add(HOST, "primary").add(SERVER, "main-one").add(INTERFACE, ifaceName);
        validateResponse(primaryClient.execute(read));

        ModelNode remove = new ModelNode();
        remove.get(OP).set(REMOVE);
        remove.get(OP_ADDR).add(HOST, "primary").add(INTERFACE, ifaceName);
        validateResponse(primaryClient.execute(remove));

        validateFailedResponse(primaryClient.execute(read));
    }

    @Test
    public void testAddRemoveSocketBindingGroup() throws Exception {
        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        final String bindingGroupName = "testing-binding-group";
        final String serverGroupName = "testing-server-group";

        // add binding group
        ModelNode add = new ModelNode();
        add.get(OP).set(ADD);
        add.get(OP_ADDR).add(SOCKET_BINDING_GROUP, bindingGroupName);
        add.get(DEFAULT_INTERFACE).set("public");
        validateResponse(primaryClient.execute(add));

        // add server group using new binding group
        add = new ModelNode();
        add.get(OP).set(ADD);
        add.get(OP_ADDR).add(SERVER_GROUP, serverGroupName);
        add.get(PROFILE).set("default");
        add.get(SOCKET_BINDING_GROUP).set(bindingGroupName);
        validateResponse(primaryClient.execute(add));

        // remove server group
        ModelNode remove = new ModelNode();
        remove.get(OP).set(REMOVE);
        remove.get(OP_ADDR).add(SERVER_GROUP, serverGroupName);
        validateResponse(primaryClient.execute(remove));

        // remove binding group
        remove = new ModelNode();
        remove.get(OP).set(REMOVE);
        remove.get(OP_ADDR).add(SOCKET_BINDING_GROUP, bindingGroupName);
        validateResponse(primaryClient.execute(remove));

        // check the binding group is gone
        ModelNode read = new ModelNode();
        add.get(OP).set(READ_RESOURCE_OPERATION);
        add.get(OP_ADDR).add(SOCKET_BINDING_GROUP, bindingGroupName);
        validateFailedResponse(primaryClient.execute(read));

    }

    /**
     * Test for AS7-3643
     */
    @Test
    public void testCanFindServerRestartRequiredAfterChangingSocketBindingPortOffset() throws Exception {
        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        ModelNode read = new ModelNode();
        read.get(OP).set(READ_ATTRIBUTE_OPERATION);
        read.get(OP_ADDR).add(HOST, "primary").add(SERVER_CONFIG, "main-one");
        read.get(NAME).set("socket-binding-port-offset");
        ModelNode result = validateResponse(primaryClient.execute(read));
        int original = result.isDefined() ? result.asInt() : 0;

        //The bug causing AS7-3643 caused execution of this op to fail
        ModelNode write = new ModelNode();
        write.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        write.get(OP_ADDR).add(HOST, "primary").add(SERVER_CONFIG, "main-one");
        write.get(NAME).set("socket-binding-port-offset");
        write.get(VALUE).set(original + 1);
        ModelNode response = primaryClient.execute(write);
        validateResponse(response);

        final String mainServerGroup = "main-server-group";
        Assert.assertEquals(SUCCESS, response.get(SERVER_GROUPS, mainServerGroup, HOST, "primary", "main-one", RESPONSE, OUTCOME).asString());
        ModelNode headers = response.get(SERVER_GROUPS, mainServerGroup, HOST, "primary", "main-one", RESPONSE, RESPONSE_HEADERS);
        Assert.assertEquals(RELOAD_REQUIRED, headers.get(PROCESS_STATE).asString());
        Assert.assertTrue(RELOAD_REQUIRED, headers.get(OPERATION_REQUIRES_RELOAD).asBoolean());


        //Now just set back to the original
        write.get(VALUE).set(original);
        validateResponse(primaryClient.execute(write));
    }

    @Test
    public void testCannotRemoveUsedServerGroup() throws Exception {

        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(REMOVE);
        operation.get(OP_ADDR).add(SERVER_GROUP, "main-server-group");

        validateFailedResponse(primaryClient.execute(operation));
    }

    @Test
    public void testAddRemoveServerConfig() throws Exception {

        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        final ModelNode serverAddress = new ModelNode();
        serverAddress.add(HOST, "secondary");
        serverAddress.add(SERVER_CONFIG, "test-server");

        final ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        composite.get(OP_ADDR).setEmptyList();

        final ModelNode steps = composite.get(STEPS).setEmptyList();

        final ModelNode step1 = steps.add();
        step1.get(OP).set(ADD);
        step1.get(OP_ADDR).set(serverAddress);
        step1.get(GROUP).set("main-server-group");
        step1.get(AUTO_START).set(false);

        final ModelNode step2 = steps.add();
        step2.get(OP).set(ADD);
        step2.get(OP_ADDR).set(serverAddress).add(SYSTEM_PROPERTY, "test-prop");
        step2.get(VALUE).set("test");

        try {
            final ModelNode response = primaryClient.execute(composite);
            validateResponse(response);
        } finally {
            final ModelNode remove = new ModelNode();
            remove.get(OP).set(REMOVE);
            remove.get(OP_ADDR).set(serverAddress);
            primaryClient.execute(remove);
        }

    }


    @Test
    public void testQueryOperations() throws Exception {

        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(QUERY);
        operation.get(OP_ADDR).add("host", "*");

        operation.get(SELECT).add("name");
        operation.get(SELECT).add("running-mode");
        operation.get(WHERE).add("primary", "false");

        ModelNode response = primaryClient.execute(operation);

        validateResponse(response);

        List<ModelNode> results = response.get(RESULT).asList();
        Assert.assertEquals(1, results.size());

        ModelNode result = results.get(0).get(RESULT);
        Assert.assertEquals(2, result.asPropertyList().size());
        Assert.assertTrue(result.hasDefined("name"));
        Assert.assertTrue(result.hasDefined("running-mode"));
        Assert.assertEquals(result.get("name").asString(), "secondary");
        Assert.assertEquals(result.get("running-mode").asString(), "NORMAL");
    }

    /** WFCORE-3730 */
    @Test
    public void testDomainRelativeToDomainBaseDir() throws IOException {
        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        PathAddress pa = PathAddress.pathAddress("path", "wfcore-3730");
        ModelNode operation = Util.createAddOperation(pa);
        operation.get("path").set("wfcore-3730");
        operation.get("relative-to").set("jboss.domain.base.dir");
        ModelNode response = primaryClient.execute(operation);
        validateResponse(response);

        operation = Util.createRemoveOperation(pa);
        response = primaryClient.execute(operation);
        validateResponse(response);
    }

    /** WFCORE-3730 */
    @Test
    public void testHostRelativeToDomainBaseDir() throws IOException {
        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        PathAddress pa = PathAddress.pathAddress("host", "primary").append("path", "wfcore-3730");
        ModelNode operation = Util.createAddOperation(pa);
        operation.get("path").set("wfcore-3730");
        operation.get("relative-to").set("jboss.domain.base.dir");
        ModelNode response = primaryClient.execute(operation);
        validateResponse(response);

        operation = Util.createRemoveOperation(pa);
        response = primaryClient.execute(operation);
        validateResponse(response);

    }

    /** WFCORE-5960 */
    @Test
    public void testHostRelativeToDomainBaseDirConfiguration() throws Exception {
        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        final PathAddress hostAddress = PathAddress.pathAddress("host", "primary");
        final Path domainDirectory = Paths.get(testSupport.getDomainPrimaryConfiguration().getDomainDirectory(), "audit.log");

        Assert.assertFalse("Found an existing audit.log at " + domainDirectory.toAbsolutePath().getParent() + ". The test cannot continue.", Files.exists(domainDirectory));

        PathAddress pa = hostAddress
                .append("subsystem", "elytron")
                .append("file-audit-log", "local-audit");

        ModelNode operation = Util.getReadAttributeOperation(pa, "relative-to");
        ModelNode response = primaryClient.execute(operation);
        ModelNode initialValue = validateResponse(response);

        try {
            operation = Util.getWriteAttributeOperation(pa, "relative-to", "jboss.domain.base.dir");
            DomainTestUtils.executeForResult(operation, primaryClient);
            reloadHost(domainPrimaryLifecycleUtil, hostAddress);
            Assert.assertTrue("Cannot found " + domainDirectory.toAbsolutePath(), Files.exists(domainDirectory));
        } finally {
            operation = Util.getWriteAttributeOperation(pa, "relative-to", initialValue.asString());
            DomainTestUtils.executeForResult(operation, primaryClient);
            reloadHost(domainPrimaryLifecycleUtil, hostAddress);
            Files.deleteIfExists(domainDirectory);
        }
    }

    private void testCannotInvokeManagedServerOperationsComposite(ModelNode stepAddress) throws Exception {
        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        ModelNode composite = new ModelNode();
        composite.get(OP).set(CompositeOperationHandler.NAME);
        composite.get(OP_ADDR).setEmptyList();
        composite.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);

        ModelNode goodServerOp = new ModelNode();
        goodServerOp.get(OP).set(READ_RESOURCE_OPERATION);
        goodServerOp.get(OP_ADDR).set(stepAddress);
        composite.get(STEPS).add(goodServerOp);
        composite.get(STEPS).add(getAddWorkerOperation(stepAddress.clone().add("worker", ("cannot-" + workerName++))));

        ModelNode result = primaryClient.execute(composite);

        validateFailedResponse(result);

        Set<String> keys = new HashSet<String>(result.get(RESULT).keys());
        keys.remove(SERVER_GROUPS);
        Assert.assertEquals(2, keys.size());

        String errorCode = getNotAuthorizedErrorCode();
        List<Property> steps = result.get(RESULT).asPropertyList();
        int i = 0;
        for (Property property : steps) {
            if (property.getName().equals(SERVER_GROUPS)) {
                continue;
            }
            ModelNode stepResult = property.getValue();
            Assert.assertEquals(FAILED, stepResult.get(OUTCOME).asString());
            if (i == 0) {
                Assert.assertFalse(stepResult.hasDefined(FAILURE_DESCRIPTION));
            }
            if (i++ == 1) {
                ModelNode desc = validateFailedResponse(stepResult);
                Assert.assertTrue(desc.toString() + " does not contain " + errorCode, desc.toString().contains(errorCode));
            }
            i++;
        }
    }

    private void testCannotInvokeManagedServerOperationsComposite(String host, String server, ModelNode stepAddress) throws Exception {
        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        ModelNode composite = new ModelNode();
        composite.get(OP).set(CompositeOperationHandler.NAME);
        composite.get(OP_ADDR).add(HOST, host);
        composite.get(OP_ADDR).add(SERVER, server);
        composite.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);

        ModelNode goodServerOp = new ModelNode();
        goodServerOp.get(OP).set(READ_RESOURCE_OPERATION);
        goodServerOp.get(OP_ADDR).set(stepAddress);
        composite.get(STEPS).add(goodServerOp);
        composite.get(STEPS).add(getAddWorkerOperation(stepAddress.clone().add("worker", ("cannot-" + workerName++))));

        ModelNode result = primaryClient.execute(composite);

        validateFailedResponse(result);

        Set<String> keys = new HashSet<String>(result.get(RESULT).keys());
        keys.remove(SERVER_GROUPS);
        Assert.assertEquals(2, keys.size());

        String errorCode = getNotAuthorizedErrorCode();
        List<Property> steps = result.get(RESULT).asPropertyList();
        int i = 0;
        for (Property property : steps) {
            if (property.getName().equals(SERVER_GROUPS)) {
                continue;
            }
            ModelNode stepResult = property.getValue();
            Assert.assertEquals(FAILED, stepResult.get(OUTCOME).asString());
            if (i == 0) {
                Assert.assertFalse(stepResult.hasDefined(FAILURE_DESCRIPTION));
            }
            if (i++ == 1) {
                ModelNode desc = validateFailedResponse(stepResult);
                Assert.assertTrue(desc.toString() + " does not contain " + errorCode, desc.toString().contains(errorCode));
            }
            i++;
        }
    }

    private ModelNode getAddWorkerOperation(ModelNode address) {

        ModelNode serverTf = new ModelNode();
        serverTf.get(OP).set("add");
        serverTf.get(OP_ADDR).set(address);

        return serverTf;
    }

    private String getNotAuthorizedErrorCode() {
        try {
            throw ControllerLogger.ROOT_LOGGER.modelUpdateNotAuthorized("", PathAddress.EMPTY_ADDRESS);
        }catch(Exception e) {
            String msg = e.getMessage();
            return msg.substring(0, msg.indexOf(":"));
        }
    }

    private void testSnapshot(ModelNode addr) throws Exception {
        final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        ModelNode snapshotOperation = new ModelNode();
        snapshotOperation.get(OP).set(SnapshotTakeHandler.DEFINITION.getName());
        snapshotOperation.get(OP_ADDR).set(addr);
        ModelNode response = primaryClient.execute(snapshotOperation);
        final String snapshot = validateResponse(response).asString();
        Assert.assertNotNull(snapshot);
        Assert.assertFalse(snapshot.isEmpty());

        ModelNode listSnapshotOperation = new ModelNode();
        listSnapshotOperation.get(OP).set(SnapshotListHandler.DEFINITION.getName());
        listSnapshotOperation.get(OP_ADDR).set(addr);
        ModelNode listResult = validateResponse(primaryClient.execute(listSnapshotOperation));
        Set<String> snapshots = new HashSet<String>();
        for (ModelNode curr : listResult.get(NAMES).asList()) {
            snapshots.add(listResult.get(DIRECTORY).asString() + fileSeparator + curr.asString());
        }

        Assert.assertTrue(listResult.toString() + " has " + snapshot, snapshots.contains(snapshot));

        ModelNode deleteSnapshotOperation = new ModelNode();
        deleteSnapshotOperation.get(OP).set(SnapshotDeleteHandler.DEFINITION.getName());
        deleteSnapshotOperation.get(OP_ADDR).set(addr);
        deleteSnapshotOperation.get(NAME).set(snapshot.substring(snapshot.lastIndexOf(fileSeparator)  + fileSeparator.length()));
        validateResponse(primaryClient.execute(deleteSnapshotOperation));

        listResult = validateResponse(primaryClient.execute(listSnapshotOperation));
        snapshots = new HashSet<String>();
        for (ModelNode curr : listResult.get(NAMES).asList()) {
            snapshots.add(listResult.get(DIRECTORY).asString() + fileSeparator + curr.asString());
        }

        Assert.assertFalse(snapshots.contains(snapshot));
    }


    private static ModelNode getSystemPropertyAddOperation(ModelNode address, String value, Boolean boottime) {
        ModelNode operation = getEmptyOperation(ADD, address);
        if (value != null) {
            operation.get(VALUE).set(value);
        }
        if (boottime != null) {
            operation.get(BOOT_TIME).set(boottime);
        }
        return operation;
    }

    private static ModelNode getSystemPropertyRemoveOperation(ModelNode address) {
        return getEmptyOperation(REMOVE, address);
    }

    private static ModelNode getReadAttributeOperation(ModelNode address, String attribute) {
        ModelNode result = getEmptyOperation(READ_ATTRIBUTE_OPERATION, address);
        result.get(NAME).set(attribute);
        return result;
    }

    private static ModelNode getReadResourceOperation(ModelNode address) {
        ModelNode result = getEmptyOperation(READ_RESOURCE_OPERATION, address);
        result.get(RECURSIVE).set(true);
        return result;
    }

    private static ModelNode getWriteAttributeOperation(ModelNode address, String value) {
        ModelNode result = getEmptyOperation(WRITE_ATTRIBUTE_OPERATION, address);
        result.get(NAME).set(VALUE);
        result.get(VALUE).set(value);
        return result;
    }

    private static ModelNode getReadChildrenNamesOperation(ModelNode address, String type) {
        ModelNode result = getEmptyOperation(READ_CHILDREN_NAMES_OPERATION, address);
        result.get(CHILD_TYPE).set(type);
        return result;
    }

    private static ModelNode getEmptyOperation(String operationName, ModelNode address) {
        ModelNode op = new ModelNode();
        op.get(OP).set(operationName);
        if (address != null) {
            op.get(OP_ADDR).set(address);
        }
        else {
            // Just establish the standard structure; caller can fill in address later
            op.get(OP_ADDR);
        }
        return op;
    }

    private static void validateBootSystemProperty(DomainClient client, ModelNode serverAddress, boolean existInModel, int origPropCount, String value) throws IOException, MgmtOperationException {
        ModelNode response = client.execute(getReadChildrenNamesOperation(serverAddress, SYSTEM_PROPERTY));
        if(existInModel) {
            ModelNode properties = validateResponse(response);
            assertThat(properties.asList().size(), is(origPropCount + 1));
            ModelNode property = validateResponse(client.execute(getReadResourceOperation(serverAddress.clone().add(SYSTEM_PROPERTY, BOOT_PROPERTY_NAME))));
            assertThat(property.hasDefined(VALUE), is(true));
            assertThat(property.get(VALUE).asString(), is(value));
        } else {
            ModelNode properties = validateResponse(response);
            assertThat("We have found " + properties.asList(), properties.asList().size(), is(origPropCount));
            ModelNode property = validateFailedResponse(client.execute(getReadResourceOperation(serverAddress.clone().add(SYSTEM_PROPERTY, BOOT_PROPERTY_NAME))));
            assertThat(property.hasDefined(VALUE), is(false));
        }
        ServiceActivatorDeploymentUtil.validateProperties(client, PathAddress.pathAddress(serverAddress), BOOT_TEST_PROPERTIES);
    }

    private ModelNode getPropertyAddress(ModelNode basePropAddress, String propName) {
        PathAddress addr = PathAddress.pathAddress(basePropAddress);
        PathAddress copy = PathAddress.EMPTY_ADDRESS;
        for (PathElement element : addr) {
            if (!element.getKey().equals(SYSTEM_PROPERTY)) {
                copy = copy.append(element);
            } else {
                copy = copy.append(PathElement.pathElement(SYSTEM_PROPERTY, propName));
            }
        }
        return copy.toModelNode();
    }

    private ModelNode getResolveExpressionOperation(String propName, ModelNode address) {
        ModelNode op = new ModelNode();
        op.get(OP).set("resolve-expression");
        op.get(OP_ADDR).set(address);
        op.get("expression").set(new ValueExpression("${" + propName + "}"));
        return op;
    }

    private void restartServer(DomainClient client, PathAddress serverAddress) throws IOException {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set("restart");
        operation.get(OP_ADDR).set(serverAddress.toModelNode());
        operation.get(BLOCKING).set(true);
        ModelNode response = client.execute(operation);
        validateResponse(response, true);
        Assert.assertTrue(checkServerState(client, serverAddress, "STARTED"));
    }

    private DomainClient reloadHost(DomainLifecycleUtil util, PathAddress host) throws Exception {
        ModelNode reload = Util.createEmptyOperation("reload", host);
        reload.get(BLOCKING).set("true");
        util.executeAwaitConnectionClosed(reload);
        util.connect();
        util.awaitHostController(System.currentTimeMillis());
        util.awaitServers(System.currentTimeMillis());

        return util.createDomainClient();
    }
}
