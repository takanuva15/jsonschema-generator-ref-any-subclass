package utils;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.generator.Module;

public class RefInsertModule implements Module {

	private static final String PACKAGE_FOR_EXTERNAL_REFS = "org.example.model";
	private static final String EXTERNAL_REF_PREFIX = "classpath:///schemas/";
	private static final String EXTERNAL_REF_SUFFIX = "-schema.yml";

	@Override
	public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
		SchemaRefDefinitionProvider definitionProvider = new SchemaRefDefinitionProvider(PACKAGE_FOR_EXTERNAL_REFS,
				EXTERNAL_REF_PREFIX, EXTERNAL_REF_SUFFIX);
		builder
				.withObjectMapper(new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)))
				.forTypesInGeneral()
				.withCustomDefinitionProvider(definitionProvider);
	}

	static class SchemaRefDefinitionProvider implements CustomDefinitionProviderV2 {

		private final String packageForExternalRefs;
		private final String externalRefPrefix;
		private final String externalRefSuffix;
		private Class<?> mainType;

		SchemaRefDefinitionProvider(String packageForExternalRefs, String externalRefPrefix, String externalRefSuffix) {
			this.packageForExternalRefs = packageForExternalRefs;
			this.externalRefPrefix = externalRefPrefix;
			this.externalRefSuffix = externalRefSuffix;
		}

		@Override
		public CustomDefinition provideCustomSchemaDefinition(ResolvedType javaType, SchemaGenerationContext context) {
			Class<?> erasedType = javaType.getErasedType();
			if (this.mainType == null) {
				this.mainType = erasedType;
			} else if (!this.isMainType(javaType)
					&& erasedType.getPackage() != null
					&& erasedType.getPackage().getName().startsWith(this.packageForExternalRefs)) {
				ObjectNode schema = context.getGeneratorConfig().createObjectNode()
						.put(context.getKeyword(SchemaKeyword.TAG_REF), this.getExternalRef(javaType));
				return new CustomDefinition(schema, CustomDefinition.INLINE_DEFINITION, CustomDefinition.INCLUDING_ATTRIBUTES);
			}
			return null;
		}

		boolean isMainType(ResolvedType javaType) {
			return this.mainType == javaType.getErasedType();
		}

		String getExternalRef(ResolvedType javaType) {
			return this.externalRefPrefix + javaType.getErasedType().getSimpleName() + this.externalRefSuffix;
		}

		@Override
		public void resetAfterSchemaGenerationFinished() {
			this.mainType = null;
		}
	}
}
