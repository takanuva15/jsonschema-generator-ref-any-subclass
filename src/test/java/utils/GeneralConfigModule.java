package utils;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.generator.Module;

public class GeneralConfigModule implements Module {
	@Override
	public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
		InsertSchemaPropsProvider descriptionProvider = new InsertSchemaPropsProvider();
		builder.forTypesInGeneral()
				.withCustomDefinitionProvider(descriptionProvider)
				.withTypeAttributeOverride(descriptionProvider);
	}

	private class InsertSchemaPropsProvider implements CustomDefinitionProviderV2, TypeAttributeOverrideV2 {

		private ResolvedType mainType = null;

		@Override
		public CustomDefinition provideCustomSchemaDefinition(ResolvedType javaType, SchemaGenerationContext context) {
			if (this.mainType == null) {
				this.mainType = javaType;
			}
			return null;
		}

		@Override
		public void overrideTypeAttributes(ObjectNode collectedTypeAttributes, TypeScope scope, SchemaGenerationContext context) {
			if (this.mainType == scope.getType()) {
				collectedTypeAttributes.put(context.getKeyword(SchemaKeyword.TAG_UNEVALUATED_PROPERTIES), false);
			}
		}

		@Override
		public void resetAfterSchemaGenerationFinished() {
			this.mainType = null;
		}
	}
}
