/*
 * Copyright (c) 2012, 2019 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.jersey.client;

import org.glassfish.jersey.internal.guava.Preconditions;

import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.of;
import static org.glassfish.jersey.client.ParameterMarshaller.parameterMarshaller;

/**
 * Jersey implementation of {@link javax.ws.rs.client.WebTarget JAX-RS client target}
 * contract.
 *
 * @author Marek Potociar
 */
public class JerseyWebTarget implements javax.ws.rs.client.WebTarget, Initializable<JerseyWebTarget> {

    private final ClientConfig config;
    private final UriBuilder targetUri;
    private final ParameterMarshaller marshaller;

    /**
     * Create new web target instance.
     *
     * @param uri    target URI.
     * @param parent parent client.
     */
    /*package*/ JerseyWebTarget(String uri, JerseyClient parent) {
        this(UriBuilder.fromUri(uri), parent.getConfiguration());
    }

    /**
     * Create new web target instance.
     *
     * @param uri    target URI.
     * @param parent parent client.
     */
    /*package*/ JerseyWebTarget(URI uri, JerseyClient parent) {
        this(UriBuilder.fromUri(uri), parent.getConfiguration());
    }

    /**
     * Create new web target instance.
     *
     * @param uriBuilder builder for the target URI.
     * @param parent     parent client.
     */
    /*package*/ JerseyWebTarget(UriBuilder uriBuilder, JerseyClient parent) {
        this(uriBuilder.clone(), parent.getConfiguration());
    }

    /**
     * Create new web target instance.
     *
     * @param link   link to the target URI.
     * @param parent parent client.
     */
    /*package*/ JerseyWebTarget(Link link, JerseyClient parent) {
        // TODO handle relative links
        this(UriBuilder.fromUri(link.getUri()), parent.getConfiguration());
    }

    /**
     * Create new web target instance.
     *
     * @param uriBuilder builder for the target URI.
     * @param that       original target to copy the internal data from.
     */
    protected JerseyWebTarget(UriBuilder uriBuilder, JerseyWebTarget that) {
        this(uriBuilder, that.config);
    }

    /**
     * Create new web target instance.
     *
     * @param uriBuilder   builder for the target URI.
     * @param clientConfig target configuration.
     */
    protected JerseyWebTarget(UriBuilder uriBuilder, ClientConfig clientConfig) {
        clientConfig.checkClient();

        this.targetUri = uriBuilder;
        this.config = clientConfig.snapshot();
        this.marshaller = parameterMarshaller(config);
    }

    @Override
    public URI getUri() {
        checkNotClosed();
        try {
            return targetUri.build();
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private void checkNotClosed() {
        config.getClient().checkNotClosed();
    }

    @Override
    public UriBuilder getUriBuilder() {
        checkNotClosed();
        return targetUri.clone();
    }

    @Override
    public JerseyWebTarget path(String path) throws NullPointerException {
        checkNotClosed();
        Preconditions.checkNotNull(path, "path is 'null'.");

        return new JerseyWebTarget(getUriBuilder().path(path), this);
    }

    @Override
    public JerseyWebTarget matrixParam(String name, Object... values) throws NullPointerException {
        checkNotClosed();
        Preconditions.checkNotNull(name, "Matrix parameter name must not be 'null'.");

        if (values == null || values.length == 0 || (values.length == 1 && values[0] == null)) {
            return new JerseyWebTarget(getUriBuilder().replaceMatrixParam(name, (Object[]) null), this);
        }

        checkForNullValues(name, values);
        return new JerseyWebTarget(getUriBuilder().matrixParam(name, marshalling(values)), this);
    }

    @Override
    public JerseyWebTarget queryParam(String name, Object... values) throws NullPointerException {
        checkNotClosed();
        return new JerseyWebTarget(JerseyWebTarget.setQueryParam(getUriBuilder(), name, marshalling(values)), this);
    }

    private static UriBuilder setQueryParam(UriBuilder uriBuilder, String name, Object[] values) {
        if (values == null || values.length == 0 || (values.length == 1 && values[0] == null)) {
            return uriBuilder.replaceQueryParam(name, (Object[]) null);
        }

        checkForNullValues(name, values);
        return uriBuilder.queryParam(name, values);
    }

    private static void checkForNullValues(String name, Object[] values) {
        Preconditions.checkNotNull(name, "name is 'null'.");

        List<Integer> indexes = new LinkedList<Integer>();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                indexes.add(i);
            }
        }
        final int failedIndexCount = indexes.size();
        if (failedIndexCount > 0) {
            final String valueTxt;
            final String indexTxt;
            if (failedIndexCount == 1) {
                valueTxt = "value";
                indexTxt = "index";
            } else {
                valueTxt = "values";
                indexTxt = "indexes";
            }

            throw new NullPointerException(
                    String.format("'null' %s detected for parameter '%s' on %s : %s",
                            valueTxt, name, indexTxt, indexes.toString()));
        }
    }

    private Object marshalling(Object object) {
        if (Collection.class.isInstance(object)) {
            return ((Collection) object).stream().map(marshaller::marshall).collect(toList());
        }
        return marshaller.marshall(object);
    }

    private Object[] marshalling(Object[] objects) {
        if (objects == null) {
            return null;
        }
        return of(objects).map(marshaller::marshall).toArray();
    }

    @Override
    public JerseyInvocation.Builder request() {
        checkNotClosed();
        JerseyInvocation.Builder b = new JerseyInvocation.Builder(getUri(), config.snapshot());
        return onBuilder(b);
    }

    @Override
    public JerseyInvocation.Builder request(String... acceptedResponseTypes) {
        checkNotClosed();
        JerseyInvocation.Builder b = new JerseyInvocation.Builder(getUri(), config.snapshot());
        onBuilder(b).request().accept(acceptedResponseTypes);
        return b;
    }

    @Override
    public JerseyInvocation.Builder request(MediaType... acceptedResponseTypes) {
        checkNotClosed();
        JerseyInvocation.Builder b = new JerseyInvocation.Builder(getUri(), config.snapshot());
        onBuilder(b).request().accept(acceptedResponseTypes);
        return b;
    }

    @Override
    public JerseyWebTarget resolveTemplate(String name, Object value) throws NullPointerException {
        return resolveTemplate(name, value, true);
    }

    @Override
    public JerseyWebTarget resolveTemplate(String name, Object value, boolean encodeSlashInPath) throws NullPointerException {
        checkNotClosed();
        Preconditions.checkNotNull(name, "name is 'null'.");
        Preconditions.checkNotNull(value, "value is 'null'.");

        return new JerseyWebTarget(getUriBuilder().resolveTemplate(name, marshalling(value), encodeSlashInPath), this);
    }

    @Override
    public JerseyWebTarget resolveTemplateFromEncoded(String name, Object value)
            throws NullPointerException {
        checkNotClosed();
        Preconditions.checkNotNull(name, "name is 'null'.");
        Preconditions.checkNotNull(value, "value is 'null'.");

        return new JerseyWebTarget(getUriBuilder().resolveTemplateFromEncoded(name, value), this);
    }

    @Override
    public JerseyWebTarget resolveTemplates(Map<String, Object> templateValues) throws NullPointerException {
        return resolveTemplates(templateValues, true);
    }

    @Override
    public JerseyWebTarget resolveTemplates(Map<String, Object> templateValues, boolean encodeSlashInPath)
            throws NullPointerException {
        checkNotClosed();
        checkTemplateValues(templateValues);

        if (templateValues.isEmpty()) {
            return this;
        } else {
            return new JerseyWebTarget(getUriBuilder().resolveTemplates(templateValues, encodeSlashInPath), this);
        }
    }

    @Override
    public JerseyWebTarget resolveTemplatesFromEncoded(Map<String, Object> templateValues)
            throws NullPointerException {
        checkNotClosed();
        checkTemplateValues(templateValues);

        if (templateValues.isEmpty()) {
            return this;
        } else {
            return new JerseyWebTarget(getUriBuilder().resolveTemplatesFromEncoded(templateValues), this);
        }
    }

    /**
     * Check template values for {@code null} values. Throws {@code NullPointerException} if the name-value map or any of the
     * names or encoded values in the map is {@code null}.
     *
     * @param templateValues map to check.
     * @throws NullPointerException if the name-value map or any of the names or encoded values in the map
     * is {@code null}.
     */
    private void checkTemplateValues(final Map<String, Object> templateValues) throws NullPointerException {
        Preconditions.checkNotNull(templateValues, "templateValues is 'null'.");

        for (final Map.Entry entry : templateValues.entrySet()) {
            Preconditions.checkNotNull(entry.getKey(), "name is 'null'.");
            Preconditions.checkNotNull(entry.getValue(), "value is 'null'.");
        }
    }

    @Override
    public JerseyWebTarget register(Class<?> providerClass) {
        checkNotClosed();
        config.register(providerClass);
        return this;
    }

    @Override
    public JerseyWebTarget register(Object provider) {
        checkNotClosed();
        config.register(provider);
        return this;
    }

    @Override
    public JerseyWebTarget register(Class<?> providerClass, int bindingPriority) {
        checkNotClosed();
        config.register(providerClass, bindingPriority);
        return this;
    }

    @Override
    public JerseyWebTarget register(Class<?> providerClass, Class<?>... contracts) {
        checkNotClosed();
        config.register(providerClass, contracts);
        return this;
    }

    @Override
    public JerseyWebTarget register(Class<?> providerClass, Map<Class<?>, Integer> contracts) {
        checkNotClosed();
        config.register(providerClass, contracts);
        return this;
    }

    @Override
    public JerseyWebTarget register(Object provider, int bindingPriority) {
        checkNotClosed();
        config.register(provider, bindingPriority);
        return this;
    }

    @Override
    public JerseyWebTarget register(Object provider, Class<?>... contracts) {
        checkNotClosed();
        config.register(provider, contracts);
        return this;
    }

    @Override
    public JerseyWebTarget register(Object provider, Map<Class<?>, Integer> contracts) {
        checkNotClosed();
        config.register(provider, contracts);
        return this;
    }

    @Override
    public JerseyWebTarget property(String name, Object value) {
        checkNotClosed();
        config.property(name, value);
        return this;
    }

    @Override
    public ClientConfig getConfiguration() {
        checkNotClosed();
        return config.getConfiguration();
    }

    @Override
    public JerseyWebTarget preInitialize() {
        config.preInitialize();
        return this;
    }

    @Override
    public String toString() {
        return "JerseyWebTarget { " + targetUri.toTemplate() + " }";
    }

    private static JerseyInvocation.Builder onBuilder(JerseyInvocation.Builder builder) {
        new InvocationBuilderListenerStage(builder.request().getInjectionManager()).invokeListener(builder);
        return builder;
    }
}
