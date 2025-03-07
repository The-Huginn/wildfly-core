/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.descriptions;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REPLY_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUEST_PROPERTIES;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Uses an analysis of registry metadata to provide a default description of an operation that adds a resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DefaultResourceAddDescriptionProvider implements DescriptionProvider {


    private static AttributeDefinition INDEX = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.ADD_INDEX, ModelType.INT, true).build();

    private final ImmutableManagementResourceRegistration registration;
    final ResourceDescriptionResolver descriptionResolver;
    final boolean orderedChildResource;

    public DefaultResourceAddDescriptionProvider(final ImmutableManagementResourceRegistration registration,
                                                 final ResourceDescriptionResolver descriptionResolver) {
        this(registration, descriptionResolver, false);
    }

    public DefaultResourceAddDescriptionProvider(final ImmutableManagementResourceRegistration registration,
            final ResourceDescriptionResolver descriptionResolver,
            final boolean orderedChildResource) {
        this.registration = registration;
        this.descriptionResolver = descriptionResolver;
        this.orderedChildResource = orderedChildResource;
    }

    @Override
    public ModelNode getModelDescription(Locale locale) {
        ModelNode result = new ModelNode();

        final ResourceBundle bundle = descriptionResolver.getResourceBundle(locale);
        result.get(OPERATION_NAME).set(ADD);
        result.get(DESCRIPTION).set(descriptionResolver.getOperationDescription(ADD, locale, bundle));

        // Sort the attribute descriptions based on attribute group and then attribute name
        Set<String> attributeNames = registration.getAttributeNames(PathAddress.EMPTY_ADDRESS);

        Map<AttributeDefinition.NameAndGroup, ModelNode> sortedDescriptions = new TreeMap<>();
        for (String attr : attributeNames)  {
            AttributeAccess attributeAccess = registration.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attr);
            if (attributeAccess.getStorageType() == AttributeAccess.Storage.CONFIGURATION) {
                AttributeDefinition def = attributeAccess.getAttributeDefinition();
                if (def != null) {
                    if (!def.isResourceOnly()){
                        addAttributeDescriptionToMap(sortedDescriptions, def, locale, bundle);
                    }
                } else {
                    // Just stick in a placeholder;
                    sortedDescriptions.put(new AttributeDefinition.NameAndGroup(attr), new ModelNode());
                }
            }
        }

        if (orderedChildResource) {
            //Add the index property to the add operation
            addAttributeDescriptionToMap(sortedDescriptions, INDEX, locale, bundle);
        }

        // Store the sorted descriptions into the overall result
        final ModelNode params = result.get(REQUEST_PROPERTIES).setEmptyObject();
        for (Map.Entry<AttributeDefinition.NameAndGroup, ModelNode> entry : sortedDescriptions.entrySet()) {
            params.get(entry.getKey().getName()).set(entry.getValue());
        }

        //This is auto-generated so don't add any access constraints

        result.get(REPLY_PROPERTIES).setEmptyObject();


        return result;
    }

    private void addAttributeDescriptionToMap(Map<AttributeDefinition.NameAndGroup, ModelNode> sortedDescriptions, AttributeDefinition def, Locale locale, ResourceBundle bundle) {
        ModelNode attrDesc = new ModelNode();
        // def will add the description to attrDesc under "request-properties" => { attr
        def.addOperationParameterDescription(attrDesc, ADD, descriptionResolver, locale, bundle);
        sortedDescriptions.put(new AttributeDefinition.NameAndGroup(def), attrDesc.get(REQUEST_PROPERTIES, def.getName()));
    }
}
