/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALL_SERVICES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILDREN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEFAULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXCEPTIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXECUTE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODEL_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NO_SERVICES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESOURCE_SERVICES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_REQUIRED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STORAGE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.INCLUDE_ALIASES;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.LOCALE;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.PROXIES;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.RECURSIVE;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.RECURSIVE_DEPTH;
import static org.jboss.as.controller.operations.global.ReadOperationNamesHandler.isVisible;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.UnauthorizedException;
import org.jboss.as.controller.access.Action.ActionEffect;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.AuthorizationResult.Decision;
import org.jboss.as.controller.access.ResourceAuthorization;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.AliasEntry;
import org.jboss.as.controller.registry.AliasStepHandler;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.AttributeAccess.Storage;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.NotificationEntry;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * {@link org.jboss.as.controller.OperationStepHandler} querying the complete type description of a given model node.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ReadResourceDescriptionHandler extends GlobalOperationHandlers.AbstractMultiTargetHandler {

    private static final SimpleAttributeDefinition INHERITED = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.INHERITED, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    private static final SimpleAttributeDefinition OPERATIONS = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.OPERATIONS, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    private static final SimpleAttributeDefinition NOTIFICATIONS = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NOTIFICATIONS, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    private static final SimpleAttributeDefinition ACCESS_CONTROL = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ACCESS_CONTROL, ModelType.STRING)
            .setRequired(false)
            .setDefaultValue(new ModelNode(AccessControl.NONE.toString()))
            .setValidator(EnumValidator.create(AccessControl.class, AccessControl.NONE, AccessControl.COMBINED_DESCRIPTIONS, AccessControl.TRIM_DESCRIPTONS))
            .build();


    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(READ_RESOURCE_DESCRIPTION_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(OPERATIONS, NOTIFICATIONS, INHERITED, RECURSIVE, RECURSIVE_DEPTH, PROXIES, INCLUDE_ALIASES, ACCESS_CONTROL, LOCALE)
            .setReadOnly()
            .setReplyType(ModelType.OBJECT)
            .build();

    static final OperationStepHandler INSTANCE = new ReadResourceDescriptionHandler();

    //Placeholder for NoSuchResourceExceptions coming from proxies to remove the child in ReadResourceDescriptionAssemblyHandler
    private static final ModelNode PROXY_NO_SUCH_RESOURCE;
    static {
        //Create something non-used since we cannot
        ModelNode none = new ModelNode();
        none.get("no-such-resource").set("no$such$resource");
        none.protect();
        PROXY_NO_SUCH_RESOURCE = none;
    }

    private ReadResourceDescriptionHandler() {
        super(true);
    }

    ReadResourceDescriptionAccessControlContext getAccessControlContext() {
        return null;
    }

    @Override
    void doExecute(OperationContext context, ModelNode operation, FilteredData filteredData, boolean ignoreMissingResource) throws OperationFailedException {
        final PathAddress address = context.getCurrentAddress();
        ReadResourceDescriptionAccessControlContext accessControlContext = getAccessControlContext() == null ? new ReadResourceDescriptionAccessControlContext(address, null) : getAccessControlContext();
        doExecute(context, operation, accessControlContext);
    }

    void doExecute(OperationContext context, ModelNode operation, ReadResourceDescriptionAccessControlContext accessControlContext) throws OperationFailedException {
        if (accessControlContext.parentAddresses == null) {
            doExecuteInternal(context, operation, accessControlContext);
        } else {
            try {
                doExecuteInternal(context, operation, accessControlContext);
            } catch (Resource.NoSuchResourceException | UnauthorizedException nsre) {
                context.getResult().set(new ModelNode());
            }
        }
    }

    private void doExecuteInternal(final OperationContext context, final ModelNode operation, final ReadResourceDescriptionAccessControlContext accessControlContext) throws OperationFailedException {

        for (AttributeDefinition def : DEFINITION.getParameters()) {
            def.validateOperation(operation);
        }

        final String opName = operation.require(OP).asString();
        PathAddress opAddr = PathAddress.pathAddress(operation.get(OP_ADDR));
        // WFCORE-76
        final boolean recursive = GlobalOperationHandlers.getRecursive(context, operation);
        final boolean proxies = PROXIES.resolveModelAttribute(context, operation).asBoolean();
        final boolean ops = OPERATIONS.resolveModelAttribute(context, operation).asBoolean();
        final boolean nots = NOTIFICATIONS.resolveModelAttribute(context, operation).asBoolean();
        final boolean aliases = INCLUDE_ALIASES.resolveModelAttribute(context, operation).asBoolean();
        final boolean inherited = INHERITED.resolveModelAttribute(context, operation).asBoolean();
        final AccessControl accessControl = AccessControl.forName(ACCESS_CONTROL.resolveModelAttribute(context, operation).asString());


        final ImmutableManagementResourceRegistration registry = getResourceRegistrationCheckForAlias(context, opAddr, accessControlContext);

        final DescriptionProvider descriptionProvider = registry.getModelDescription(PathAddress.EMPTY_ADDRESS);
        final Locale locale = GlobalOperationHandlers.getLocale(context, operation);

        final ModelNode nodeDescription = descriptionProvider.getModelDescription(locale);
        final Map<String, ModelNode> operations = ops ? new HashMap<String, ModelNode>() : null;
        final Map<String, ModelNode> notifications = nots ? new HashMap<String, ModelNode>() : null;
        final Map<PathElement, ModelNode> childResources = recursive ? new HashMap<PathElement, ModelNode>() : Collections.<PathElement, ModelNode>emptyMap();

        if (accessControl != AccessControl.NONE) {
            accessControlContext.initLocalResourceAddresses(context, opAddr);
        }


        // We're going to add a bunch of steps that should immediately follow this one. We are going to add them
        // in reverse order of how they should execute, as that is the way adding a Stage.IMMEDIATE step works
        // Last to execute is the handler that assembles the overall response from the pieces created by all the other steps
        final ReadResourceDescriptionAssemblyHandler assemblyHandler = new ReadResourceDescriptionAssemblyHandler(nodeDescription, operations, notifications, childResources, accessControlContext, accessControl);
        context.addStep(assemblyHandler, OperationContext.Stage.MODEL, true);

        //Let's filter the children
        if (!aliases && nodeDescription.hasDefined(CHILDREN)) {
            for (Property child : nodeDescription.get(CHILDREN).asPropertyList()) {
                String key = child.getName();
                if (isGlobalAlias(registry, child)) {
                    nodeDescription.get(CHILDREN).remove(key);
                }
            }
        }

        if (ops) {
            for (final Map.Entry<String, OperationEntry> entry : registry.getOperationDescriptions(PathAddress.EMPTY_ADDRESS, inherited).entrySet()) {
                OperationEntry operationEntry = entry.getValue();
                if (isVisible(operationEntry, context)) {
                    ReadOperationDescriptionHandler.DescribedOp describedOp = new ReadOperationDescriptionHandler.DescribedOp(operationEntry, locale);
                    operations.put(entry.getKey(), describedOp.getDescription());
                }
            }
        }

        if (nots) {
            for (final Map.Entry<String, NotificationEntry> entry : registry.getNotificationDescriptions(PathAddress.EMPTY_ADDRESS, inherited).entrySet()) {
                final DescriptionProvider provider = entry.getValue().getDescriptionProvider();
                notifications.put(entry.getKey(), provider.getModelDescription(locale));
            }
        }

        if (nodeDescription.hasDefined(ATTRIBUTES)) {
            for (final String attr : nodeDescription.require(ATTRIBUTES).keys()) {
                final AttributeAccess access = registry.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attr);
                // If there is metadata for an attribute but no AttributeAccess, assume RO. Can't
                // be writable without a registered handler. This opens the possibility that out-of-date metadata
                // for attribute "foo" can lead to a read of non-existent-in-model "foo" with
                // an unexpected undefined value returned. But it removes the possibility of a
                // dev forgetting to call registry.registerReadOnlyAttribute("foo", null) resulting
                // in the valid attribute "foo" not being readable
                final AttributeAccess.AccessType accessType = access == null ? AttributeAccess.AccessType.READ_ONLY : access.getAccessType();
                final AttributeAccess.Storage storage = access == null ? AttributeAccess.Storage.CONFIGURATION : access.getStorageType();
                final ModelNode attrNode = nodeDescription.get(ATTRIBUTES, attr);
                //AS7-3085 - For a domain mode server show writable attributes as read-only
                String displayedAccessType =
                        context.getProcessType() == ProcessType.DOMAIN_SERVER && storage == AttributeAccess.Storage.CONFIGURATION ?
                                AttributeAccess.AccessType.READ_ONLY.toString() : accessType.toString();
                attrNode.get(ACCESS_TYPE).set(displayedAccessType);
                attrNode.get(STORAGE).set(storage.toString());
                if (accessType == AttributeAccess.AccessType.READ_WRITE) {
                    Set<AttributeAccess.Flag> flags = access.getFlags();
                    if (flags.contains(AttributeAccess.Flag.RESTART_ALL_SERVICES)) {
                        attrNode.get(RESTART_REQUIRED).set(ALL_SERVICES);
                    } else if (flags.contains(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)) {
                        attrNode.get(RESTART_REQUIRED).set(RESOURCE_SERVICES);
                    } else if (flags.contains(AttributeAccess.Flag.RESTART_JVM)) {
                        attrNode.get(RESTART_REQUIRED).set(JVM);
                    } else {
                        attrNode.get(RESTART_REQUIRED).set(NO_SERVICES);
                    }
                }
            }
        }

        if (accessControl != AccessControl.NONE) {
            accessControlContext.checkResourceAccess(context, registry, nodeDescription, operations);
        }

        if (recursive) {
            for (final PathElement element : registry.getChildAddresses(PathAddress.EMPTY_ADDRESS)) {
                PathAddress relativeAddr = PathAddress.pathAddress(element);
                ImmutableManagementResourceRegistration childReg = registry.getSubModel(relativeAddr);

                boolean readChild = true;
                if (childReg.isRemote() && !proxies) {
                    readChild = false;
                }
                if (childReg.isAlias() && !aliases) {
                    readChild = false;
                }

                if (readChild) {
                    final ModelNode rrOp = operation.clone();
                    final PathAddress address;
                    try {
                        address = PathAddress.pathAddress(opAddr, element);
                    } catch (Exception e) {
                        continue;
                    }
                    rrOp.get(OP_ADDR).set(address.toModelNode());
                    // WFCORE-76
                    GlobalOperationHandlers.setNextRecursive(context, operation, rrOp);
                    final ModelNode rrRsp = new ModelNode();
                    childResources.put(element, rrRsp);

                    final OperationStepHandler handler = getRecursiveStepHandler(childReg, opName, accessControlContext, address);
                    context.addStep(rrRsp, rrOp, handler, OperationContext.Stage.MODEL, true);
                    //Add a "child" => undefined
                    nodeDescription.get(CHILDREN, element.getKey(), MODEL_DESCRIPTION, element.getValue());
                } else if (childReg.isAlias() && !aliases) {
                    if (isSingletonResource(registry, element.getKey())) {
                        if (nodeDescription.get(CHILDREN).hasDefined(element.getKey())) {
                            nodeDescription.get(CHILDREN).get(element.getKey()).remove(element.getValue());
                        }
                    }
                }
            }
        }

        context.completeStep(new OperationContext.RollbackHandler() {
            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {

                if (!context.hasFailureDescription()) {
                    for (final ModelNode value : childResources.values()) {
                        if (value.hasDefined(FAILURE_DESCRIPTION)) {
                            context.getFailureDescription().set(value.get(FAILURE_DESCRIPTION));
                            break;
                        }
                    }
                }
            }
        });
    }

    private boolean isSingletonResource(final ImmutableManagementResourceRegistration registry, final String key) {
        return registry.getSubModel(PathAddress.pathAddress(PathElement.pathElement(key))) == null;
    }

    private boolean isGlobalAlias(final ImmutableManagementResourceRegistration registry, final Property child) {
        if(isSingletonResource(registry, child.getName())) {
            Set<PathElement> childrenPath = registry.getChildAddresses(PathAddress.EMPTY_ADDRESS);
            boolean found = false;
            boolean alias = true;
            for(PathElement childPath : childrenPath) {
                if(childPath.getKey().equals(child.getName())) {
                    found = true;
                    ImmutableManagementResourceRegistration squatterRegistration = registry.getSubModel(PathAddress.pathAddress(childPath));
                    alias = alias && (squatterRegistration != null && squatterRegistration.isAlias());
                }
            }
            if(found && alias) {
                return true;
            }
            ImmutableManagementResourceRegistration squatterRegistration = registry.getSubModel(PathAddress.pathAddress(PathElement.pathElement(child.getName(), child.getValue().asString())));
            return squatterRegistration != null && squatterRegistration.isAlias();
        }
        String key = child.getName();
        ImmutableManagementResourceRegistration wildCardChildRegistration = registry.getSubModel(PathAddress.pathAddress(PathElement.pathElement(key)));
        boolean isAlias = wildCardChildRegistration.isAlias();
        Set<String> registredNames = registry.getChildNames(PathAddress.pathAddress(PathElement.pathElement(key)));
        if (registredNames != null && !registredNames.isEmpty() && isAlias) {
            for (String value : registredNames) {
                ImmutableManagementResourceRegistration childRegistration = registry.getSubModel(PathAddress.pathAddress(PathElement.pathElement(key, value)));
                isAlias = isAlias && childRegistration != null && childRegistration.isAlias();
                if(!isAlias) {
                    return false;
                }
            }
        }
        return isAlias;
    }

    private OperationStepHandler getRecursiveStepHandler(ImmutableManagementResourceRegistration childReg, String opName, ReadResourceDescriptionAccessControlContext accessControlContext, PathAddress address) {
        OperationStepHandler overrideHandler = childReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, opName);
        if (overrideHandler != null && (overrideHandler.getClass() == ReadResourceDescriptionHandler.class || overrideHandler.getClass() == AliasStepHandler.class)) {
            // not an override
            overrideHandler = null;
        }

        if (overrideHandler != null) {
            return new NestedReadResourceDescriptionHandler(overrideHandler);
        } else {
            return new NestedReadResourceDescriptionHandler(new ReadResourceDescriptionAccessControlContext(address, accessControlContext));
        }
    }



    private ImmutableManagementResourceRegistration getResourceRegistrationCheckForAlias(OperationContext context, PathAddress opAddr, ReadResourceDescriptionAccessControlContext accessControlContext) {
        //The direct root registration is only needed if we are doing access-control=true
        final ImmutableManagementResourceRegistration root = context.getRootResourceRegistration();
        final ImmutableManagementResourceRegistration registry = root.getSubModel(opAddr);

        AliasEntry aliasEntry = registry.getAliasEntry();
        if (aliasEntry == null) {
            return registry;
        }
        //Get hold of the real registry if it was an alias
        PathAddress realAddress = aliasEntry.convertToTargetAddress(opAddr, AliasEntry.AliasContext.create(opAddr, context));
        assert !realAddress.equals(opAddr) : "Alias was not translated";

        return root.getSubModel(realAddress);
    }

    /**
     *
     * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
     */
    static final class CheckResourceAccessHandler implements OperationStepHandler {

        static final OperationDefinition DEFAULT_DEFINITION = new SimpleOperationDefinitionBuilder(GlobalOperationHandlers.CHECK_DEFAULT_RESOURCE_ACCESS, NonResolvingResourceDescriptionResolver.INSTANCE)
            .setPrivateEntry()
            .build();

        static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(GlobalOperationHandlers.CHECK_RESOURCE_ACCESS, NonResolvingResourceDescriptionResolver.INSTANCE)
            .setPrivateEntry()
            .build();

        private final boolean runtimeResource;
        private final boolean defaultSetting;
        private final ModelNode accessControlResult;
        private final ModelNode nodeDescription;
        private final Map<String, ModelNode> operations;

        CheckResourceAccessHandler(boolean runtimeResource, boolean defaultSetting, ModelNode accessControlResult, ModelNode nodeDescription, Map<String, ModelNode> operations) {
            this.runtimeResource = runtimeResource;
            this.defaultSetting = defaultSetting;
            this.accessControlResult = accessControlResult;
            this.nodeDescription = nodeDescription;
            this.operations = operations;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            ModelNode result = new ModelNode();
            boolean customDefaultCheck = operation.get(OP).asString().equals(GlobalOperationHandlers.CHECK_DEFAULT_RESOURCE_ACCESS);
            ResourceAuthorization authResp = context.authorizeResource(true, customDefaultCheck);
            if (authResp == null || authResp.getResourceResult(ActionEffect.ADDRESS).getDecision() == Decision.DENY) {
                if (!defaultSetting || authResp == null) {
                    //We are not allowed to see the resource, so we don't set the accessControlResult, meaning that the ReadResourceAssemblyHandler will ignore it for this address
                } else {
                    result.get(ActionEffect.ADDRESS.toString()).set(false);
                }
            } else {
//                if (!defaultSetting) {
//                    result.get(ADDRESS).set(operation.get(OP_ADDR));
//                }
                addResourceAuthorizationResults(result, authResp);

                ModelNode attributes = new ModelNode();
                attributes.setEmptyObject();

                if (result.get(READ).asBoolean()) {
                    if (nodeDescription.hasDefined(ATTRIBUTES)) {
                        for (Property attrProp : nodeDescription.require(ATTRIBUTES).asPropertyList()) {
                            ModelNode attributeResult = new ModelNode();
                            Storage storage = Storage.valueOf(attrProp.getValue().get(STORAGE).asString().toUpperCase(Locale.ENGLISH));
                            addAttributeAuthorizationResults(attributeResult, attrProp.getName(), authResp, storage == Storage.RUNTIME);
                            if (attributeResult.isDefined()) {
                                attributes.get(attrProp.getName()).set(attributeResult);
                            }
                        }
                    }
                    result.get(ATTRIBUTES).set(attributes);

                    if (operations != null) {
                        ModelNode ops = new ModelNode();
                        ops.setEmptyObject();
                        PathAddress currentAddress = context.getCurrentAddress();
                        for (Map.Entry<String, ModelNode> entry : operations.entrySet()) {

                            ModelNode operationToCheck = Util.createOperation(entry.getKey(), currentAddress);

                            ModelNode operationResult = new ModelNode();

                            addOperationAuthorizationResult(context, operationResult, operationToCheck, entry.getKey());

                            ops.get(entry.getKey()).set(operationResult);
                        }
                        result.get(ModelDescriptionConstants.OPERATIONS).set(ops);
                    }
                }
            }
            accessControlResult.set(result);
        }

        private void addResourceAuthorizationResults(ModelNode result, ResourceAuthorization authResp) {
            if (runtimeResource) {
                addResourceAuthorizationResult(result, authResp, ActionEffect.READ_RUNTIME);
                addResourceAuthorizationResult(result, authResp, ActionEffect.WRITE_RUNTIME);
            } else {
                addResourceAuthorizationResult(result, authResp, ActionEffect.READ_CONFIG);
                addResourceAuthorizationResult(result, authResp, ActionEffect.WRITE_CONFIG);
            }
        }

        private void addResourceAuthorizationResult(ModelNode result, ResourceAuthorization authResp, ActionEffect actionEffect) {
            AuthorizationResult authResult = authResp.getResourceResult(actionEffect);
            result.get(actionEffect == ActionEffect.READ_CONFIG || actionEffect == ActionEffect.READ_RUNTIME ? READ : WRITE).set(authResult.getDecision() == Decision.PERMIT);
        }

        private void addAttributeAuthorizationResults(ModelNode result, String attributeName, ResourceAuthorization authResp, boolean runtime) {
            if (runtime) {
                addAttributeAuthorizationResult(result, attributeName, authResp, ActionEffect.READ_RUNTIME);
                addAttributeAuthorizationResult(result, attributeName, authResp, ActionEffect.WRITE_RUNTIME);
            } else {
                addAttributeAuthorizationResult(result, attributeName, authResp, ActionEffect.READ_CONFIG);
                addAttributeAuthorizationResult(result, attributeName, authResp, ActionEffect.WRITE_CONFIG);
            }
        }

        private void addAttributeAuthorizationResult(ModelNode result, String attributeName, ResourceAuthorization authResp, ActionEffect actionEffect) {
            AuthorizationResult authorizationResult = authResp.getAttributeResult(attributeName, actionEffect);
            if (authorizationResult != null) {
                result.get(actionEffect == ActionEffect.READ_CONFIG || actionEffect == ActionEffect.READ_RUNTIME ? READ : WRITE).set(authorizationResult.getDecision() == Decision.PERMIT);
            }
        }

        private void addOperationAuthorizationResult(OperationContext context, ModelNode result, ModelNode operation, String operationName) {
            AuthorizationResult authorizationResult = context.authorizeOperation(operation);
            result.get(EXECUTE).set(authorizationResult.getDecision() == Decision.PERMIT);
        }

    }

    /**
     * Assembles the response to a read-resource request from the components gathered by earlier steps.
     */
    private static class ReadResourceDescriptionAssemblyHandler implements OperationStepHandler {

        private final ModelNode nodeDescription;
        private final Map<String, ModelNode> operations;
        private final Map<String, ModelNode> notifications;
        private final Map<PathElement, ModelNode> childResources;
        private final ReadResourceDescriptionAccessControlContext accessControlContext;
        private final AccessControl accessControl;

        /**
         * Creates a ReadResourceAssemblyHandler that will assemble the response using the contents
         * of the given maps.
         *  @param nodeDescription basic description of the node, of its attributes and of its child types
         * @param operations      descriptions of the resource's operations
         * @param notifications   descriptions of the resource's notifications
         * @param childResources  read-resource-description response from child resources, where the key is the PathAddress
         *                        relative to the address of the operation this handler is handling and the
         *                        value is the full read-resource response. Will not be {@code null}
         * @param accessControlContext context for tracking access control data
         * @param accessControl   type of access control output that is needed
         */
        private ReadResourceDescriptionAssemblyHandler(final ModelNode nodeDescription, final Map<String, ModelNode> operations,
                                                       Map<String, ModelNode> notifications, final Map<PathElement, ModelNode> childResources, final ReadResourceDescriptionAccessControlContext accessControlContext,
                                                       final AccessControl accessControl) {
            this.nodeDescription = nodeDescription;
            this.operations = operations;
            this.notifications = notifications;
            this.childResources = childResources;
            this.accessControlContext = accessControlContext;
            this.accessControl = accessControl;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            for (Map.Entry<PathElement, ModelNode> entry : childResources.entrySet()) {
                final PathElement element = entry.getKey();
                final ModelNode value = entry.getValue();
                if (!value.has(FAILURE_DESCRIPTION)) {
                    ModelNode actualValue = value.get(RESULT);
                    if (actualValue.equals(PROXY_NO_SUCH_RESOURCE)) {
                        nodeDescription.get(CHILDREN).remove(element.getKey());
                    } else {
                        nodeDescription.get(CHILDREN, element.getKey(), MODEL_DESCRIPTION, element.getValue()).set(actualValue);
                    }
                } else if (value.hasDefined(FAILURE_DESCRIPTION)) {
                    context.getFailureDescription().set(value.get(FAILURE_DESCRIPTION));
                    break;
                }
            }

            if (operations != null) {
                for (Map.Entry<String, ModelNode> entry : operations.entrySet()) {
                    nodeDescription.get(OPERATIONS.getName(), entry.getKey()).set(entry.getValue());
                }
            }

            if (notifications != null) {
                for (Map.Entry<String, ModelNode> entry : notifications.entrySet()) {
                    nodeDescription.get(NOTIFICATIONS.getName(), entry.getKey()).set(entry.getValue());
                }
            }

            if (accessControlContext.defaultWildcardAccessControl != null && accessControlContext.localResourceAccessControlResults != null) {
                ModelNode accessControl = new ModelNode();
                accessControl.setEmptyObject();

                ModelNode defaultControl;
                if (accessControlContext.defaultWildcardAccessControl != null) {
                    accessControl.get(DEFAULT).set(accessControlContext.defaultWildcardAccessControl);
                    defaultControl = accessControlContext.defaultWildcardAccessControl;
                } else {
                    //TODO this should always be present
                    defaultControl = new ModelNode();
                }


                if (accessControlContext.localResourceAccessControlResults != null) {
                    ModelNode exceptions = accessControl.get(EXCEPTIONS);
                    exceptions.setEmptyObject();
                    for (Map.Entry<PathAddress, ModelNode> entry : accessControlContext.localResourceAccessControlResults.entrySet()) {
                        if (!entry.getValue().isDefined()) {
                            //If access was denied CheckResourceAccessHandler will leave this as undefined
                            continue;
                        }
                        if (!entry.getValue().equals(defaultControl)) {
                            //This has different values to the default due to vault expressions being used for attribute values. We need to include the address
                            //in the exception modelnode for the console to be easier able to parse it
                            ModelNode exceptionAddr = entry.getKey().toModelNode();
                            ModelNode exception = entry.getValue();
                            exception.get(ADDRESS).set(exceptionAddr);
                            exceptions.get(exceptionAddr.asString()).set(entry.getValue());
                        }
                    }
                }
                nodeDescription.get(ACCESS_CONTROL.getName()).set(accessControl);
            }

            if (accessControl == AccessControl.TRIM_DESCRIPTONS) {
                //Trim unwanted data
                nodeDescription.get(ModelDescriptionConstants.DESCRIPTION).clear();
                if (nodeDescription.hasDefined(ModelDescriptionConstants.ATTRIBUTES)) {
                    nodeDescription.get(ModelDescriptionConstants.ATTRIBUTES).clear();
                }
                if (nodeDescription.hasDefined(ModelDescriptionConstants.OPERATIONS)) {
                    nodeDescription.get(ModelDescriptionConstants.OPERATIONS).clear();
                }
                if (nodeDescription.hasDefined(CHILDREN)) {
                    for (String child: nodeDescription.get(CHILDREN).keys()) {
                        ModelNode childNode = nodeDescription.get(CHILDREN, child);
                        if (childNode.isDefined()) {
                            childNode.remove(ModelDescriptionConstants.DESCRIPTION);
                        }
                    }
                }
            }
            context.getResult().set(nodeDescription);
        }
    }

    static final class ReadResourceDescriptionAccessControlContext {
        private final PathAddress opAddress;
        private final List<PathAddress> parentAddresses;
        private List<PathAddress> localResourceAddresses = null;
        private ModelNode defaultWildcardAccessControl;
        private Map<PathAddress, ModelNode> localResourceAccessControlResults = new HashMap<PathAddress, ModelNode>();

        ReadResourceDescriptionAccessControlContext(PathAddress opAddress, ReadResourceDescriptionAccessControlContext parent) {
            this.opAddress = opAddress;
            this.parentAddresses = parent != null ? parent.parentAddresses : null;
        }

        private void initLocalResourceAddresses(OperationContext context, PathAddress opAddress){
            localResourceAddresses = getLocalResourceAddresses(context, opAddress);
        }

        private List<PathAddress> getLocalResourceAddresses(OperationContext context, PathAddress opAddr){
            List<PathAddress> localResourceAddresses = null;
            if (parentAddresses == null) {
                if (opAddr.size() == 0) {
                    return Collections.singletonList(PathAddress.EMPTY_ADDRESS);
                } else {
                    localResourceAddresses = new ArrayList<>();
                    getAllActualResourceAddresses(context, localResourceAddresses, PathAddress.EMPTY_ADDRESS, opAddr);
                }
            } else {
                localResourceAddresses = new ArrayList<>();
                for (PathAddress pathAddress : parentAddresses) {
                    getAllActualResourceAddresses(context, localResourceAddresses, pathAddress, opAddr);
                }
            }
            return localResourceAddresses;

        }

        private void getAllActualResourceAddresses(OperationContext context, List<PathAddress> addresses, PathAddress currentAddress, PathAddress opAddress) {
            if (opAddress.size() == 0) {
                return;
            }

            final int length = currentAddress.size();
            final PathElement currentElement = opAddress.getElement(length);
            if (currentElement.isWildcard()) {

                Resource resource;
                try {
                    resource = context.readResourceFromRoot(currentAddress);
                } catch (UnauthorizedException e) {
                    //We could not read the resource, now check if that is due not to having access or read-config permissions
                    ResourceAuthorization response = context.authorizeResource(false, false);
                    if (response.getResourceResult(ActionEffect.ADDRESS).getDecision() != Decision.PERMIT) {
                        //We do not have access permissions
                        return;
                    }
                    //We do not have read permissions, get the resource by other means
                    //TODO revisit this, since resource.getChildXXX() should probably need some authorization as well
                    resource = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);
                    for (PathElement element : currentAddress) {
                        resource = resource.getChild(element);
                    }
                }

                ImmutableManagementResourceRegistration directRegistration = context.getRootResourceRegistration().getSubModel(currentAddress);

                Map<String, Set<String>> childAddresses = GlobalOperationHandlers.getChildAddresses(context,
                                                                                        currentAddress,
                                                                                        directRegistration,
                                                                                        resource,
                                                                                        currentElement.getKey());
                Set<String> childNames = childAddresses.get(currentElement.getKey());
                if (childNames != null && !childNames.isEmpty()) {
                    for (String name : childNames) {
                        PathAddress address = currentAddress.append(PathElement.pathElement(currentElement.getKey(), name));
                        if (addParentResource(context, addresses, address)) {
                            if (address.size() == opAddress.size()) {
                                addresses.add(address);
                            } else {
                                getAllActualResourceAddresses(context, addresses, address, opAddress);
                            }
                        }
                    }
                } else {
                    //There are no children, but for access control exception purposes,
                    // add what we have so far along with the remainder of the child
                    PathAddress addr = currentAddress.append(opAddress.subAddress(currentAddress.size()));
                    addresses.add(addr);
                }
            } else {
                PathAddress address = currentAddress.append(currentElement);
                if (addParentResource(context, addresses, address)) {
                    if (address.size() == opAddress.size()) {
                        addresses.add(address);
                    } else {
                        getAllActualResourceAddresses(context, addresses, address, opAddress);
                    }
                }
            }
        }

        private boolean addParentResource(OperationContext context, List<PathAddress> addresses, PathAddress address) {
            try {
                context.readResourceFromRoot(address);
            } catch (Resource.NoSuchResourceException nsre) {
                // Don't include the result
                return false;
            } catch (UnauthorizedException ue) {
                //We are not allowed to read it, but still we know it exists
            }
            return true;
        }

        void checkResourceAccess(final OperationContext context, final ImmutableManagementResourceRegistration registration, final ModelNode nodeDescription, Map<String, ModelNode> operations) {
            final ModelNode defaultAccess = Util.createOperation(
                    opAddress.size() > 0 && !opAddress.getLastElement().isWildcard() ?
                            GlobalOperationHandlers.CHECK_DEFAULT_RESOURCE_ACCESS : GlobalOperationHandlers.CHECK_RESOURCE_ACCESS,
                    opAddress);
            defaultWildcardAccessControl = new ModelNode();
            context.addStep(defaultAccess, new CheckResourceAccessHandler(registration.isRuntimeOnly(), true, defaultWildcardAccessControl, nodeDescription, operations), OperationContext.Stage.MODEL, true);

            for (final PathAddress address : localResourceAddresses) {
                final ModelNode op = Util.createOperation(GlobalOperationHandlers.CHECK_RESOURCE_ACCESS, address);
                final ModelNode resultHolder = new ModelNode();
                localResourceAccessControlResults.put(address, resultHolder);
                context.addStep(op, new CheckResourceAccessHandler(registration.isRuntimeOnly(), false, resultHolder, nodeDescription, operations), OperationContext.Stage.MODEL, true);
            }
        }
    }

    private class NestedReadResourceDescriptionHandler extends ReadResourceDescriptionHandler {
        final ReadResourceDescriptionAccessControlContext accessControlContext;
        final OperationStepHandler overrideStepHandler;

        NestedReadResourceDescriptionHandler(ReadResourceDescriptionAccessControlContext accessControlContext) {
            this.accessControlContext = accessControlContext;
            this.overrideStepHandler = null;
        }

        NestedReadResourceDescriptionHandler(OperationStepHandler overrideStepHandler) {
            this.accessControlContext = null;
            this.overrideStepHandler = overrideStepHandler;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            if (accessControlContext != null) {
                doExecute(context, operation, accessControlContext);
            } else {
                try {
                    overrideStepHandler.execute(context, operation);
                } catch (Resource.NoSuchResourceException e){
                    //Mark it as not accessible so that the assembly handler can remove it
                    context.getResult().set(PROXY_NO_SUCH_RESOURCE);
                } catch (UnauthorizedException e) {
                    //We were not allowed to read it, the assembly handler should still allow people to see it
                    context.getResult().set(new ModelNode());
                }
            }
        }
    }

    /**
     * For use with the access-control parameter
     */
    public enum AccessControl {
        /** No access control information should be included */
        NONE("none"),
        /** Access control information should be included alongside the resource descriptions */
        COMBINED_DESCRIPTIONS("combined-descriptions"),
        /** Access control information should be inclueded alongside the minimal resource descriptions */
        TRIM_DESCRIPTONS("trim-descriptions")
        ;


        private static final Map<String, AccessControl> MAP;

        static {
            final Map<String, AccessControl> map = new HashMap<String, AccessControl>();
            for (AccessControl directoryGrouping : values()) {
                map.put(directoryGrouping.localName, directoryGrouping);
            }
            MAP = map;
        }

        public static AccessControl forName(String localName) {
            final AccessControl value = localName != null ? MAP.get(localName.toLowerCase(Locale.ENGLISH)) : null;
            return value == null ? AccessControl.valueOf(localName.toUpperCase(Locale.ENGLISH)) : value;
        }

        private final String localName;

        AccessControl(final String localName) {
            this.localName = localName;
        }

        @Override
        public String toString() {
            return localName;
        }

        public ModelNode toModelNode() {
            return new ModelNode().set(toString());
        }
    }

}
