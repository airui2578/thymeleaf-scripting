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
package org.mybatis.scripting.thymeleaf.processor;

import org.mybatis.scripting.thymeleaf.ContextVariableNames;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.standard.expression.Assignation;
import org.thymeleaf.standard.expression.AssignationSequence;
import org.thymeleaf.standard.expression.AssignationUtils;
import org.thymeleaf.standard.expression.IStandardExpression;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * The processor class for handling the {@code mybatis:bind} tag.
 * <br>
 * This class was inspired with the {@code StandardWithTagProcessor} provide by Thymeleaf.
 * <br>
 * This processor can be access using {@code mybatis:bind}) as element tag processor.
 *
 * @author Kazuki Shimizu
 * @version 1.0.0
 * @see org.thymeleaf.standard.processor.StandardWithTagProcessor
 */
public class MyBatisBindTagProcessor extends AbstractAttributeTagProcessor {

  private static final int PRECEDENCE = 600;
  private static final String ATTR_NAME = "bind";

  /**
   * Constructor that can be specified the template mode and dialect prefix.
   * @param templateMode A target template mode
   * @param prefix A target dialect prefix
   */
  public MyBatisBindTagProcessor(final TemplateMode templateMode, final String prefix) {
    super(templateMode, prefix, null, false, ATTR_NAME, true, PRECEDENCE, true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void doProcess(ITemplateContext context, IProcessableElementTag tag, AttributeName attributeName,
          String attributeValue, IElementTagStructureHandler structureHandler) {
    AssignationSequence assignations =
        AssignationUtils.parseAssignationSequence(context, attributeValue, false);

    @SuppressWarnings("unchecked")
    Map<String, Object> customBindVariables =
        (Map<String, Object>)context.getVariable(ContextVariableNames.CUSTOM_BIND_VARS);

    List<Assignation> assignationValues = assignations.getAssignations();
    assignationValues.forEach(assignation -> {
      IStandardExpression nameExp = assignation.getLeft();
      Object name = nameExp .execute(context);
      IStandardExpression valueExp = assignation.getRight();
      Object value = valueExp.execute(context);

      final String newVariableName = name == null ? null : name.toString();
      if (StringUtils.isEmptyOrWhitespace(newVariableName)) {
        throw new TemplateProcessingException(
            "Variable name expression evaluated as null or empty: \"" + nameExp + "\"");
      }

      customBindVariables.put(newVariableName, value);
    });
  }

}
