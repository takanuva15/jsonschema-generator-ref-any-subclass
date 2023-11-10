package utils;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.generator.Module;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GeneralConfigModule implements Module {
	@Override
	public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
		InsertSchemaPropsProvider descriptionProvider = new InsertSchemaPropsProvider();
		builder.forTypesInGeneral()
				.withSubtypeResolver(new ClassGraphSubtypeResolver())
				.withCustomDefinitionProvider(descriptionProvider)
				.withTypeAttributeOverride(descriptionProvider)
				.withCustomDefinitionProvider(new ObjectIdToCustomSchemaProvider());
		builder.forMethods()
				.withIgnoreCheck(method -> method.getSchemaPropertyName().endsWith(")"));
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
			var schema = context.getGeneratorConfig().createObjectNode();
			Stream.of(String.class, ObjectId.class)
					.map(context.getTypeContext()::resolve)
					.map(anyOfOption -> context.createStandardDefinition(anyOfOption, this))
					.forEach(schema.putArray(context.getKeyword(SchemaKeyword.TAG_ANYOF))::add);
			return new CustomDefinition(schema, true);
		}
	}
}
