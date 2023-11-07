package utils;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.impl.module.SimpleTypeModule;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.bson.types.ObjectId;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class GeneralConfigModule implements Module {
	@Override
	public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
		InsertSchemaPropsProvider descriptionProvider = new InsertSchemaPropsProvider();
		builder.forTypesInGeneral()
				.withSubtypeResolver(new ClassGraphSubtypeResolver())
				.withCustomDefinitionProvider(descriptionProvider)
				.withTypeAttributeOverride(descriptionProvider);
		builder.forMethods()
				.withIgnoreCheck(method -> method.getSchemaPropertyName().endsWith(")"));
		builder
//				.with(new SimpleTypeModule().withStringType(ObjectId.class))
				.forTypesInGeneral()
				.withCustomDefinitionProvider(new ObjectIdToCustomSchemaProvider())
		;

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

	/**
	 * Simple implementation of a reflection based subtype resolver, considering only subtypes from a certain package.
	 */
	private class ClassGraphSubtypeResolver implements SubtypeResolver {

		private final ClassGraph classGraphConfig;
		private ScanResult scanResult;

		ClassGraphSubtypeResolver() {
			this.classGraphConfig = new ClassGraph()
					.enableClassInfo()
					.enableInterClassDependencies()
					// in this example, only consider a certain set of potential subtypes
					.acceptPackages("org.example.model");
		}

		private ScanResult getScanResult() {
			if (this.scanResult == null) {
				this.scanResult = this.classGraphConfig.scan();
			}
			return this.scanResult;
		}

		@Override
		public void resetAfterSchemaGenerationFinished() {
			if (this.scanResult != null) {
				this.scanResult.close();
				this.scanResult = null;
			}
		}

		@Override
		public List<ResolvedType> findSubtypes(ResolvedType declaredType, SchemaGenerationContext context) {
			if (declaredType.getErasedType() == Object.class) {
				return null;
			}
			ClassInfoList subtypes;
			if (declaredType.isInterface()) {
				subtypes = this.getScanResult().getClassesImplementing(declaredType.getErasedType());
			} else {
				subtypes = this.getScanResult().getSubclasses(declaredType.getErasedType());
			}
			if (!subtypes.isEmpty()) {
				TypeContext typeContext = context.getTypeContext();
				return subtypes.loadClasses(true)
						.stream()
						.map(subclass -> typeContext.resolveSubtype(declaredType, subclass))
						.collect(Collectors.toList());
			}
			return null;
		}
	}

	private class ObjectIdToCustomSchemaProvider implements CustomDefinitionProviderV2 {
		@Override
		public CustomDefinition provideCustomSchemaDefinition(ResolvedType javaType, SchemaGenerationContext context) {
			if (!javaType.isInstanceOf(ObjectId.class)) {
				return null;
			}
			var config = context.getGeneratorConfig();
//			var schema = config.createObjectNode().put(context.getKeyword(SchemaKeyword.TAG_TYPE), context.getKeyword(SchemaKeyword.TAG_TYPE_STRING));
			var schema = config.createObjectNode().put(context.getKeyword(SchemaKeyword.TAG_ANYOF),
					config.createArrayNode()
			);
			return new CustomDefinition(schema, true);
		}
	}
}
