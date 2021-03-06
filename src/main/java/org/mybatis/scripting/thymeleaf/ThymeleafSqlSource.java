/**
 *    Copyright 2018 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.scripting.thymeleaf;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.scripting.xmltags.DynamicContext;
import org.apache.ibatis.session.Configuration;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.IContext;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The {@code SqlSource} for integrating with Thymeleaf.
 *
 * @author Kazuki Shimizu
 * @version 1.0.0
 *
 * @see ThymeleafLanguageDriver
 * @see org.mybatis.scripting.thymeleaf.processor.MyBatisBindTagProcessor
 */
class ThymeleafSqlSource implements SqlSource {
  private final Configuration configuration;
  private final ITemplateEngine templateEngine;
  private final SqlSourceBuilder sqlSourceBuilder;
  private final String sqlTemplate;

  /**
   * Constructor for for integrating with template engine provide by Thymeleaf.
   * @param configuration A configuration instance of MyBatis
   * @param templateEngine A template engine provide by Thymeleaf
   * @param sqlTemplate A template string of SQL (inline SQL or template file path)
   */
  ThymeleafSqlSource(Configuration configuration, ITemplateEngine templateEngine, String sqlTemplate) {
    this.configuration = configuration;
    this.templateEngine = templateEngine;
    this.sqlTemplate = sqlTemplate;
    this.sqlSourceBuilder = new SqlSourceBuilder(configuration);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    DynamicContext dynamicContext = new DynamicContext(configuration, parameterObject);
    CustomBindVariablesContext context;
    if (parameterObject instanceof Map) {
      @SuppressWarnings(value = "unchecked")
      Map<String, Object> parameterMap = (Map<String, Object>) parameterObject;
      context = new MapBasedContext(parameterMap, dynamicContext);
    } else {
      MetaClass metaClass = MetaClass.forClass(parameterType, configuration.getReflectorFactory());
      context = new MetaClassBasedContext(parameterObject, metaClass, parameterType, dynamicContext);
    }

    String sql = templateEngine.process(sqlTemplate, context);

    context.getCustomBindVariables().forEach(dynamicContext::bind);

    SqlSource sqlSource = sqlSourceBuilder.parse(sql, parameterType, dynamicContext.getBindings());
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    dynamicContext.getBindings().forEach(boundSql::setAdditionalParameter);

    return boundSql;
  }

  private interface CustomBindVariablesContext extends IContext {
    Map<String, Object> getCustomBindVariables();
  }

  private static abstract class AbstractCustomBindVariablesContext implements CustomBindVariablesContext {

    private final DynamicContext dynamicContext;
    private final Map<String, Object> customBindVariable;
    private final Set<String> variableNames;

    AbstractCustomBindVariablesContext(DynamicContext dynamicContext) {
      this.dynamicContext = dynamicContext;
      this.customBindVariable = new HashMap<>();
      this.variableNames = new HashSet<>();
      addVariableNames(dynamicContext.getBindings().keySet());
      addVariableNames(Collections.singleton(ContextVariableNames.CUSTOM_BIND_VARS));
    }

    void addVariableNames(Collection<String> names) {
      variableNames.addAll(names);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Locale getLocale() {
      return Locale.getDefault();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsVariable(String name) {
      return variableNames.contains(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getVariableNames() {
      return variableNames;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getVariable(String name) {
      if (name.equals(ContextVariableNames.CUSTOM_BIND_VARS)) {
        return customBindVariable;
      }
      if (dynamicContext.getBindings().containsKey(name)) {
        return dynamicContext.getBindings().get(name);
      }
      return getParameterValue(name);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> getCustomBindVariables() {
      return customBindVariable;
    }

    abstract Object getParameterValue(String name);

  }

  private static class MapBasedContext extends AbstractCustomBindVariablesContext {

    private final Map<String,Object> variables;

    private MapBasedContext(Map<String, Object> parameterMap, DynamicContext dynamicContext) {
      super(dynamicContext);
      this.variables = parameterMap;
      addVariableNames(parameterMap.keySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getParameterValue(String name) {
      return variables.get(name);
    }

  }

  private static class MetaClassBasedContext extends AbstractCustomBindVariablesContext {

    private final Object parameterObject;
    private final MetaClass parameterMetaClass;
    private final Class<?> parameterType;

    private MetaClassBasedContext(
        Object parameterObject, MetaClass parameterMetaClass, Class<?> parameterType, DynamicContext dynamicContext) {
      super(dynamicContext);
      this.parameterObject = parameterObject;
      this.parameterMetaClass = parameterMetaClass;
      this.parameterType = parameterType;
      addVariableNames(Arrays.asList(parameterMetaClass.getGetterNames()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getParameterValue(String name) {
      try {
        return parameterMetaClass.getGetInvoker(name).invoke(parameterObject, null);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new IllegalStateException(
            String.format("Cannot get a value for property named '%s' in '%s'", name, parameterType), e);
      }
    }

  }

}
