package ai.csap.validation.strategy.context;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintViolationBuilder;
import org.hibernate.validator.internal.engine.constraintvalidation.ConstraintViolationCreationContext;
import org.hibernate.validator.internal.engine.path.PathImpl;
import org.hibernate.validator.internal.util.CollectionHelper;
import org.hibernate.validator.internal.util.Contracts;
import org.hibernate.validator.internal.util.logging.Log;
import org.hibernate.validator.internal.util.logging.LoggerFactory;
import org.hibernate.validator.messageinterpolation.ExpressionLanguageFeatureLevel;

import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ElementKind;
import jakarta.validation.metadata.ConstraintDescriptor;
import lombok.Getter;

/**
 * 验证上下文
 *
 * @Author ycf
 * @Date 2022/9/9 00:13
 * @Version 1.0
 */
public class ApiConstraintValidatorContextImpl implements ConstraintValidatorContext, HibernateConstraintValidatorContext {
    private static final Log LOG = LoggerFactory.make(MethodHandles.lookup());
    private Map<String, Object> messageParameters;
    private Map<String, Object> expressionVariables;
    @Getter
    private final ClockProvider clockProvider;
    private final ExpressionLanguageFeatureLevel defaultConstraintExpressionLanguageFeatureLevel;
    private final ExpressionLanguageFeatureLevel defaultCustomViolationExpressionLanguageFeatureLevel;
    private final PathImpl basePath;
    private final ConstraintDescriptor<?> constraintDescriptor;
    private List<ConstraintViolationCreationContext> constraintViolationCreationContexts;
    private boolean defaultDisabled;
    private Object dynamicPayload;
    private final Object constraintValidatorPayload;

    public ApiConstraintValidatorContextImpl(ClockProvider clockProvider, PathImpl propertyPath,
                                             ConstraintDescriptor<?> constraintDescriptor,
                                             Object constraintValidatorPayload,
                                             ExpressionLanguageFeatureLevel defaultConstraintExpressionLanguageFeatureLevel,
                                             ExpressionLanguageFeatureLevel defaultCustomViolationExpressionLanguageFeatureLevel) {
        this.clockProvider = clockProvider;
        this.defaultConstraintExpressionLanguageFeatureLevel = defaultConstraintExpressionLanguageFeatureLevel;
        this.defaultCustomViolationExpressionLanguageFeatureLevel = defaultCustomViolationExpressionLanguageFeatureLevel;
        this.basePath = propertyPath;
        this.constraintDescriptor = constraintDescriptor;
        this.constraintValidatorPayload = constraintValidatorPayload;
    }

    public final void disableDefaultConstraintViolation() {
        this.defaultDisabled = true;
    }

    public final String getDefaultConstraintMessageTemplate() {
        return this.constraintDescriptor.getMessageTemplate();
    }

    public HibernateConstraintViolationBuilder buildConstraintViolationWithTemplate(String messageTemplate) {
        return new ApiConstraintValidatorContextImpl.ConstraintViolationBuilderImpl(messageTemplate, this.getCopyOfBasePath());
    }

    public <T> T unwrap(Class<T> type) {
        if (type.isAssignableFrom(HibernateConstraintValidatorContext.class)) {
            return (T) type.cast(this);
        } else {
            throw LOG.getTypeNotSupportedForUnwrappingException(type);
        }
    }

    public HibernateConstraintValidatorContext addExpressionVariable(String name, Object value) {
        Contracts.assertNotNull(name, "null is not a valid value for an expression variable name");
        if (this.expressionVariables == null) {
            this.expressionVariables = new HashMap<>();
        }

        this.expressionVariables.put(name, value);
        return this;
    }

    public HibernateConstraintValidatorContext addMessageParameter(String name, Object value) {
        Contracts.assertNotNull(name, "null is not a valid value for a parameter name");
        if (this.messageParameters == null) {
            this.messageParameters = new HashMap<>();
        }

        this.messageParameters.put(name, value);
        return this;
    }

    public HibernateConstraintValidatorContext withDynamicPayload(Object violationContext) {
        this.dynamicPayload = violationContext;
        return this;
    }

    public <C> C getConstraintValidatorPayload(Class<C> type) {
        return (C) (this.constraintValidatorPayload != null &&
                type.isAssignableFrom(this.constraintValidatorPayload.getClass()) ?
                type.cast(this.constraintValidatorPayload) : null);
    }

    public final ConstraintDescriptor<?> getConstraintDescriptor() {
        return this.constraintDescriptor;
    }

    public final List<ConstraintViolationCreationContext> getConstraintViolationCreationContexts() {
        if (this.defaultDisabled) {
            if (this.constraintViolationCreationContexts != null && !this.constraintViolationCreationContexts.isEmpty()) {
                return CollectionHelper.toImmutableList(this.constraintViolationCreationContexts);
            } else {
                throw LOG.getAtLeastOneCustomMessageMustBeCreatedException();
            }
        } else if (this.constraintViolationCreationContexts != null && !this.constraintViolationCreationContexts.isEmpty()) {
            List<ConstraintViolationCreationContext> returnedConstraintViolationCreationContexts = new ArrayList<>(this.constraintViolationCreationContexts.size() + 1);
            returnedConstraintViolationCreationContexts.addAll(this.constraintViolationCreationContexts);
            returnedConstraintViolationCreationContexts.add(this.getDefaultConstraintViolationCreationContext());
            return CollectionHelper.toImmutableList(returnedConstraintViolationCreationContexts);
        } else {
            return Collections.singletonList(this.getDefaultConstraintViolationCreationContext());
        }
    }

    protected final PathImpl getCopyOfBasePath() {
        return PathImpl.createCopy(this.basePath);
    }

    private ConstraintViolationCreationContext getDefaultConstraintViolationCreationContext() {
        Map<String, Object> params = this.messageParameters != null ?
                new HashMap<>(this.messageParameters) : Collections.emptyMap();
        Map<String, Object> vars = this.expressionVariables != null ?
                new HashMap<>(this.expressionVariables) : Collections.emptyMap();
        return new ConstraintViolationCreationContext(
                this.getDefaultConstraintMessageTemplate(),
                this.defaultConstraintExpressionLanguageFeatureLevel,
                false,
                this.basePath,
                params,
                vars,
                this.dynamicPayload);
    }

    private abstract class NodeBuilderBase {
        protected final String messageTemplate;
        protected ExpressionLanguageFeatureLevel expressionLanguageFeatureLevel;
        protected PathImpl propertyPath;

        protected NodeBuilderBase(String template, PathImpl path) {
            this(template, ApiConstraintValidatorContextImpl.this.defaultCustomViolationExpressionLanguageFeatureLevel, path);
        }

        protected NodeBuilderBase(String template, ExpressionLanguageFeatureLevel expressionLanguageFeatureLevel, PathImpl path) {
            this.messageTemplate = template;
            this.expressionLanguageFeatureLevel = expressionLanguageFeatureLevel;
            this.propertyPath = path;
        }

        public ConstraintValidatorContext addConstraintViolation() {
            if (ApiConstraintValidatorContextImpl.this.constraintViolationCreationContexts == null) {
                ApiConstraintValidatorContextImpl.this.constraintViolationCreationContexts = CollectionHelper.newArrayList(3);
            }

            if (ApiConstraintValidatorContextImpl.this.expressionVariables != null &&
                    !ApiConstraintValidatorContextImpl.this.expressionVariables.isEmpty() &&
                    this.expressionLanguageFeatureLevel == ExpressionLanguageFeatureLevel.NONE) {
                Class<? extends Annotation> annotationType = ApiConstraintValidatorContextImpl.this.constraintDescriptor.getAnnotation() != null ?
                        ApiConstraintValidatorContextImpl.this.constraintDescriptor.getAnnotation().annotationType() :
                        Annotation.class;
                ApiConstraintValidatorContextImpl.LOG.expressionVariablesDefinedWithExpressionLanguageNotEnabled(annotationType);
            }

            Map<String, Object> params = ApiConstraintValidatorContextImpl.this.messageParameters != null ?
                    new HashMap<>(ApiConstraintValidatorContextImpl.this.messageParameters) :
                    Collections.emptyMap();
            Map<String, Object> vars = ApiConstraintValidatorContextImpl.this.expressionVariables != null ?
                    new HashMap<>(ApiConstraintValidatorContextImpl.this.expressionVariables) :
                    Collections.emptyMap();
            ConstraintViolationCreationContext context = new ConstraintViolationCreationContext(
                    this.messageTemplate,
                    this.expressionLanguageFeatureLevel,
                    true,
                    this.propertyPath,
                    params,
                    vars,
                    ApiConstraintValidatorContextImpl.this.dynamicPayload);
            ApiConstraintValidatorContextImpl.this.constraintViolationCreationContexts.add(context);
            return ApiConstraintValidatorContextImpl.this;
        }
    }

    protected class ConstraintViolationBuilderImpl extends ApiConstraintValidatorContextImpl.NodeBuilderBase implements HibernateConstraintViolationBuilder {
        protected ConstraintViolationBuilderImpl(String template, PathImpl path) {
            super(template, path);
        }

        public HibernateConstraintViolationBuilder enableExpressionLanguage(ExpressionLanguageFeatureLevel expressionLanguageFeatureLevel) {
            this.expressionLanguageFeatureLevel = ExpressionLanguageFeatureLevel.interpretDefaultForCustomViolations(expressionLanguageFeatureLevel);
            return this;
        }

        /**
         * @deprecated
         */
        @Deprecated
        public ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderDefinedContext addNode(String name) {
            this.dropLeafNodeIfRequired();
            this.propertyPath.addPropertyNode(name);
            return ApiConstraintValidatorContextImpl.this.new NodeBuilder(this.messageTemplate, this.expressionLanguageFeatureLevel, this.propertyPath);
        }

        public ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext addPropertyNode(String name) {
            this.dropLeafNodeIfRequired();
            return ApiConstraintValidatorContextImpl.this.new DeferredNodeBuilder(
                    this.messageTemplate, this.expressionLanguageFeatureLevel,
                    this.propertyPath, name, ElementKind.PROPERTY);
        }

        public ConstraintValidatorContext.ConstraintViolationBuilder.LeafNodeBuilderCustomizableContext addBeanNode() {
            return ApiConstraintValidatorContextImpl.this.new DeferredNodeBuilder(
                    this.messageTemplate, this.expressionLanguageFeatureLevel,
                    this.propertyPath, (String) null, ElementKind.BEAN);
        }

        public ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderDefinedContext addParameterNode(int index) {
            throw ApiConstraintValidatorContextImpl.LOG.getParameterNodeAddedForNonCrossParameterConstraintException(this.propertyPath);
        }

        public ConstraintValidatorContext.ConstraintViolationBuilder.ContainerElementNodeBuilderCustomizableContext
                addContainerElementNode(String name, Class<?> containerType, Integer typeArgumentIndex) {
            this.dropLeafNodeIfRequired();
            return ApiConstraintValidatorContextImpl.this.new DeferredNodeBuilder(
                    this.messageTemplate, this.expressionLanguageFeatureLevel,
                    this.propertyPath, name, containerType, typeArgumentIndex);
        }

        private void dropLeafNodeIfRequired() {
            if (this.propertyPath.getLeafNode().getKind() == ElementKind.BEAN) {
                this.propertyPath = PathImpl.createCopyWithoutLeafNode(this.propertyPath);
            }

        }
    }

    protected class NodeBuilder extends ApiConstraintValidatorContextImpl.NodeBuilderBase
            implements ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderDefinedContext,
            ConstraintValidatorContext.ConstraintViolationBuilder.LeafNodeBuilderDefinedContext,
            ConstraintValidatorContext.ConstraintViolationBuilder.ContainerElementNodeBuilderDefinedContext {
        protected NodeBuilder(String template, ExpressionLanguageFeatureLevel expressionLanguageFeatureLevel, PathImpl path) {
            super(template, expressionLanguageFeatureLevel, path);
        }

        /**
         * @deprecated
         */
        @Deprecated
        public ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext addNode(String name) {
            return this.addPropertyNode(name);
        }

        public ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext addPropertyNode(String name) {
            return ApiConstraintValidatorContextImpl.this.new DeferredNodeBuilder(
                    this.messageTemplate, this.expressionLanguageFeatureLevel,
                    this.propertyPath, name, ElementKind.PROPERTY);
        }

        public ConstraintValidatorContext.ConstraintViolationBuilder.LeafNodeBuilderCustomizableContext addBeanNode() {
            return ApiConstraintValidatorContextImpl.this.new DeferredNodeBuilder(
                    this.messageTemplate, this.expressionLanguageFeatureLevel,
                    this.propertyPath, null, ElementKind.BEAN);
        }

        public ConstraintValidatorContext.ConstraintViolationBuilder.ContainerElementNodeBuilderCustomizableContext
                addContainerElementNode(String name, Class<?> containerType, Integer typeArgumentIndex) {
            return ApiConstraintValidatorContextImpl.this.new DeferredNodeBuilder(
                    this.messageTemplate, this.expressionLanguageFeatureLevel,
                    this.propertyPath, name, containerType, typeArgumentIndex);
        }
    }

    private final class DeferredNodeBuilder extends ApiConstraintValidatorContextImpl.NodeBuilderBase
            implements ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext,
            ConstraintValidatorContext.ConstraintViolationBuilder.LeafNodeBuilderCustomizableContext,
            ConstraintValidatorContext.ConstraintViolationBuilder.NodeContextBuilder,
            ConstraintValidatorContext.ConstraintViolationBuilder.LeafNodeContextBuilder,
            ConstraintValidatorContext.ConstraintViolationBuilder.ContainerElementNodeBuilderCustomizableContext,
            ConstraintValidatorContext.ConstraintViolationBuilder.ContainerElementNodeContextBuilder {
        private final String leafNodeName;
        private final ElementKind leafNodeKind;
        private final Class<?> leafNodeContainerType;
        private final Integer leafNodeTypeArgumentIndex;

        private DeferredNodeBuilder(String template, ExpressionLanguageFeatureLevel expressionLanguageFeatureLevel, PathImpl path, String nodeName, ElementKind leafNodeKind) {
            super(template, expressionLanguageFeatureLevel, path);
            this.leafNodeName = nodeName;
            this.leafNodeKind = leafNodeKind;
            this.leafNodeContainerType = null;
            this.leafNodeTypeArgumentIndex = null;
        }

        private DeferredNodeBuilder(String template,
                                     ExpressionLanguageFeatureLevel expressionLanguageFeatureLevel,
                                     PathImpl path, String nodeName,
                                     Class<?> leafNodeContainerType,
                                     Integer leafNodeTypeArgumentIndex) {
            super(template, expressionLanguageFeatureLevel, path);
            this.leafNodeName = nodeName;
            this.leafNodeKind = ElementKind.CONTAINER_ELEMENT;
            this.leafNodeContainerType = leafNodeContainerType;
            this.leafNodeTypeArgumentIndex = leafNodeTypeArgumentIndex;
        }

        public ApiConstraintValidatorContextImpl.DeferredNodeBuilder inIterable() {
            this.propertyPath.makeLeafNodeIterable();
            return this;
        }

        public ApiConstraintValidatorContextImpl.DeferredNodeBuilder inContainer(Class<?> containerClass, Integer typeArgumentIndex) {
            this.propertyPath.setLeafNodeTypeParameter(containerClass, typeArgumentIndex);
            return this;
        }

        public ApiConstraintValidatorContextImpl.NodeBuilder atKey(Object key) {
            this.propertyPath.makeLeafNodeIterableAndSetMapKey(key);
            this.addLeafNode();
            return ApiConstraintValidatorContextImpl.this.new NodeBuilder(this.messageTemplate, this.expressionLanguageFeatureLevel, this.propertyPath);
        }

        public ApiConstraintValidatorContextImpl.NodeBuilder atIndex(Integer index) {
            this.propertyPath.makeLeafNodeIterableAndSetIndex(index);
            this.addLeafNode();
            return ApiConstraintValidatorContextImpl.this.new NodeBuilder(this.messageTemplate, this.expressionLanguageFeatureLevel, this.propertyPath);
        }

        /**
         * @deprecated
         */
        @Deprecated
        public ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext addNode(String name) {
            return this.addPropertyNode(name);
        }

        public ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext addPropertyNode(String name) {
            this.addLeafNode();
            return ApiConstraintValidatorContextImpl.this.new DeferredNodeBuilder(
                    this.messageTemplate, this.expressionLanguageFeatureLevel,
                    this.propertyPath, name, ElementKind.PROPERTY);
        }

        public ConstraintValidatorContext.ConstraintViolationBuilder.ContainerElementNodeBuilderCustomizableContext
                addContainerElementNode(String name, Class<?> containerType, Integer typeArgumentIndex) {
            this.addLeafNode();
            return ApiConstraintValidatorContextImpl.this.new DeferredNodeBuilder(
                    this.messageTemplate, this.expressionLanguageFeatureLevel,
                    this.propertyPath, name, containerType, typeArgumentIndex);
        }

        public ConstraintValidatorContext.ConstraintViolationBuilder.LeafNodeBuilderCustomizableContext addBeanNode() {
            this.addLeafNode();
            return ApiConstraintValidatorContextImpl.this.new DeferredNodeBuilder(
                    this.messageTemplate, this.expressionLanguageFeatureLevel,
                    this.propertyPath, (String) null, ElementKind.BEAN);
        }

        public ConstraintValidatorContext addConstraintViolation() {
            this.addLeafNode();
            return super.addConstraintViolation();
        }

        private void addLeafNode() {
            switch (this.leafNodeKind) {
                case BEAN:
                    this.propertyPath.addBeanNode();
                    break;
                case PROPERTY:
                    this.propertyPath.addPropertyNode(this.leafNodeName);
                    break;
                case CONTAINER_ELEMENT:
                    this.propertyPath.setLeafNodeTypeParameter(this.leafNodeContainerType, this.leafNodeTypeArgumentIndex);
                    this.propertyPath.addContainerElementNode(this.leafNodeName);
                    break;
                default:
                    throw new IllegalStateException("Unsupported node kind: " + String.valueOf(this.leafNodeKind));
            }

        }
    }

}
