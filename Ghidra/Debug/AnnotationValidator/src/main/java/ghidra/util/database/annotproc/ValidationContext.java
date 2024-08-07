/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.util.database.annotproc;

import java.util.*;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import ghidra.util.database.annot.DBAnnotatedField;

public class ValidationContext {
	final Types typeUtils;
	final Elements elementUtils;
	final Messager messager;

	final TypeElement LIST_ELEM;
	final TypeElement DB_ANNOTATED_OBJECT_ELEM;
	final TypeElement DB_OBJECT_COLUMN_ELEM;
	final TypeElement DB_FIELD_CODEC_ELEM;
	final TypeElement DEFAULT_CODEC_ELEM;
	final TypeElement ENUM_ELEM;

	/**
	 * Construct a Validation Context with the specified processing environment/
	 *
	 * @param env the processing environment
	 */
	public ValidationContext(ProcessingEnvironment env) {
		typeUtils = env.getTypeUtils();
		elementUtils = env.getElementUtils();
		messager = env.getMessager();

		LIST_ELEM = elementUtils.getTypeElement(List.class.getCanonicalName());
		DB_ANNOTATED_OBJECT_ELEM =
			elementUtils.getTypeElement("ghidra.util.database.DBAnnotatedObject");
		DB_OBJECT_COLUMN_ELEM = elementUtils.getTypeElement("ghidra.util.database.DBObjectColumn");
		DB_FIELD_CODEC_ELEM = elementUtils.getTypeElement(
			"ghidra.util.database.DBCachedObjectStoreFactory.DBFieldCodec");
		DEFAULT_CODEC_ELEM = elementUtils.getTypeElement(
			DBAnnotatedField.class.getCanonicalName() + ".DefaultCodec");
		ENUM_ELEM = elementUtils.getTypeElement(Enum.class.getCanonicalName());
	}

	/**
	 * Check if t1 is a subclass of t2.
	 * 
	 * @param t1 the potential subclass
	 * @param t2 the potential superclass
	 * @return true if t1 is a subclass of t2, false otherwise
	 */
	public boolean isSubclass(TypeElement t1, TypeElement t2) {
		return typeUtils.isSubtype(typeUtils.erasure(t1.asType()), typeUtils.erasure(t2.asType()));
	}

	/**
	 * Check if the field has the specified type.
	 * 
	 * @param field the field element
	 * @param type the type element
	 * @return true if the field has the specified type, false otherwise
	 */
	public boolean hasType(VariableElement field, TypeElement type) {
		return hasType(field, type.asType());
	}

	/**
	 * Check if the field has the specified type.
	 * 
	 * @param field the field element
	 * @param type the type mirror
	 * @return true if the field has the specified type, false otherwise
	 */
	public boolean hasType(VariableElement field, TypeMirror type) {
		TypeMirror fieldType = field.asType();
		try {
			PrimitiveType unboxed = typeUtils.unboxedType(type);
			if (typeUtils.isSameType(fieldType, unboxed)) {
				return true;
			}
		}
		catch (IllegalArgumentException e) {
			// Eh, I guess it's not unboxable
		}

		if (fieldType.getKind() == TypeKind.DECLARED) {
			DeclaredType declType = (DeclaredType) fieldType;
			if (isSubclass((TypeElement) declType.asElement(), ENUM_ELEM)) {
				Map<String, TypeMirror> enumArgs = getArguments(declType, ENUM_ELEM);
				TypeMirror argE = enumArgs.get("E");
				if (typeUtils.isSameType(declType, argE)) {
					return true;
				}
			}
		}

		return typeUtils.isAssignable(fieldType, type);
		// return typeUtils.isSameType(fieldType, type);
	}

	/**
	 * Check if t1 is capturable by t2.
	 * 
	 * @param t1 is the type to check
	 * @param t2 the capture target type
	 * @return true if t1 is capturable by t2, false otherwise
	 */
	public boolean isCapturable(TypeMirror t1, TypeMirror t2) {
		// TODO: This only works for typevar at top level...
		// TODO: Need to figure out how to check for capture and check
		if (t2.getKind() == TypeKind.TYPEVAR) {
			TypeVariable v2 = (TypeVariable) t2;
			if (!typeUtils.isSubtype(t1, v2.getUpperBound())) {
				return false;
			}
			if (!typeUtils.isSubtype(v2.getLowerBound(), t1)) {
				return false;
			}
			return true;
		}
		return typeUtils.isSubtype(t1, t2);
	}

	/**
	 * Check if the type is an enum type.
	 * 
	 * @param t the type mirror to check
	 * @return true if the type is an enum type, false otherwise
	 */
	public boolean isEnumType(TypeMirror t) {
		if (t.getKind() != TypeKind.DECLARED) {
			return false;
		}
		DeclaredType enumType = typeUtils.getDeclaredType(ENUM_ELEM, t);
		return typeUtils.isSubtype(t, enumType);
	}

	/**
	 * Find the supertype of a set of declared types that matches the specified super type.
	 * 
	 * @param types the set of declared types
	 * @param superType the super type element to match
	 * @return the matching declared type, or null if no match is found
	 */
	protected DeclaredType findSupertype(Set<DeclaredType> types, TypeElement superType) {
		Set<DeclaredType> next;
		while (!types.isEmpty()) {
			next = new HashSet<>();
			for (DeclaredType t : types) {
				List<? extends TypeMirror> supers = typeUtils.directSupertypes(t);
				for (TypeMirror s : supers) {
					DeclaredType ds = (DeclaredType) s;
					if (superType == ds.asElement()) {
						return ds;
					}
					next.add(ds);
				}
			}
			types = next;
		}
		return null;
	}

	/**
	 * Find the supertype of a declared type that matches the specified super type element.
	 * 
	 * @param type the declared type
	 * @param superElem the super type element to match
	 * @return the matching declared type, or null if no match is found
	 */
	public DeclaredType findSupertype(DeclaredType type, TypeElement superElem) {
		return findSupertype(Set.of(type), superElem);
	}

	/**
	 * Find the supertype of a type element that matches the specified super type element.
	 * 
	 * @param elem the type element
	 * @param superElem the super type element to match
	 * @return the matching declared type, or null if no match is found
	 */
	public DeclaredType findSupertype(TypeElement elem, TypeElement superElem) {
		return findSupertype((DeclaredType) elem.asType(), superElem);
	}

	/**
	 * Convert the type arguments of the super type element to a map.
	 * 
	 * @param superElem the super type element
	 * @param superType the declared super type
	 * @return a map of type argument names to their corresponding type mirrors
	 */
	protected Map<String, TypeMirror> toArgsMap(TypeElement superElem, DeclaredType superType) {
		List<? extends TypeParameterElement> typeParameters = superElem.getTypeParameters();
		List<? extends TypeMirror> typeArguments = superType.getTypeArguments();
		assert typeParameters.size() == typeArguments.size();
		Map<String, TypeMirror> result = new HashMap<>();
		for (int i = 0; i < typeParameters.size(); i++) {
			result.put(typeParameters.get(i).getSimpleName().toString(), typeArguments.get(i));
		}
		return result;
	}

	/**
	 * Get the type arguments of a declared type as a map.
	 * 
	 * @param type the declared type
	 * @param superElem the super type element
	 * @return a map of type argument names to their corresponding type mirrors
	 */
	public Map<String, TypeMirror> getArguments(DeclaredType type, TypeElement superElem) {
		return toArgsMap(superElem, findSupertype(type, superElem));
	}

	/**
	 * Get the type arguments of a type element as a map.
	 * 
	 * @param elem the type element
	 * @param superElem the super type element
	 * @return a map of type argument names to their corresponding type mirrors
	 */
	public Map<String, TypeMirror> getArguments(TypeElement elem, TypeElement superElem) {
		return toArgsMap(superElem, findSupertype(elem, superElem));
	}

	/**
	 * Format the given type mirror as a string.
	 * 
	 * @param type the type mirror to format
	 * @return the formatted type mirror as a string
	 */
	public String format(TypeMirror type) {
		FormatVisitor vis = new FormatVisitor();
		type.accept(vis, null);
		return vis.buf.toString();
	}
}

/**
 * Class for formatting {@link TypeMirror} instances into a readable string
 */
class FormatVisitor implements TypeVisitor<Void, Void> {
	StringBuffer buf = new StringBuffer();

	/**
	 * Visit method for {@link TypeMirror}. Delegates to specific visit methods based on the type
	 * kind.
	 * 
	 * @param t the type mirror to visit
	 * @param p unused parameter (can be {@code null})
	 * @return {@code null}
	 */
	@Override
	public Void visit(TypeMirror t, Void p) {
		switch (t.getKind()) {
			case ARRAY:
				return visitArray((ArrayType) t, p);
			case BOOLEAN:
			case BYTE:
			case CHAR:
			case DOUBLE:
			case FLOAT:
			case INT:
			case LONG:
			case SHORT:
			case VOID:
				return visitPrimitive((PrimitiveType) t, p);
			case DECLARED:
				return visitDeclared((DeclaredType) t, p);
			case ERROR:
				return visitError((ErrorType) t, p);
			case EXECUTABLE:
				return visitExecutable((ExecutableType) t, p);
			case INTERSECTION:
				return visitIntersection((IntersectionType) t, p);
			case NONE:
				return visitNoType((NoType) t, p);
			case NULL:
				return visitNull((NullType) t, p);
			case TYPEVAR:
				return visitTypeVariable((TypeVariable) t, p);
			case UNION:
				return visitUnion((UnionType) t, p);
			case WILDCARD:
				return visitWildcard((WildcardType) t, p);
			default:
				return visitUnknown(t, p);
		}
	}

	/**
	 * Visit method for {@link PrimitiveType}.
	 * 
	 * @param t the primitive type to visit
	 * @param p unused parameter (can be {@code null})
	 * @return {@code null}
	 */
	@Override
	public Void visitPrimitive(PrimitiveType t, Void p) {
		buf.append(t.toString());
		return null;
	}

	/**
	 * Visit method for {@link NullType}.
	 * 
	 * @param t the null type to visit
	 * @param p unused parameter (can be {@code null})
	 * @return {@code null}
	 */
	@Override
	public Void visitNull(NullType t, Void p) {
		buf.append(t.toString());
		return null;
	}

	/**
	 * Visit method for {@link ArrayType}.
	 * 
	 * @param t the array type to visit
	 * @param p unused parameter (can be {@code null})
	 * @return {@code null}
	 */
	@Override
	public Void visitArray(ArrayType t, Void p) {
		visit(t.getComponentType());
		buf.append("[]");
		return null;
	}

	/**
	 * Visit method for {@link DeclaredType}.
	 * 
	 * @param t the declared type to visit
	 * @param p unused parameter (can be {@code null})
	 * @return {@code null}
	 */
	@Override
	public Void visitDeclared(DeclaredType t, Void p) {
		buf.append(t.asElement().toString());
		Iterator<? extends TypeMirror> it = t.getTypeArguments().iterator();
		if (it.hasNext()) {
			buf.append("<");
			visit(it.next());
			while (it.hasNext()) {
				buf.append(", ");
				visit(it.next());
			}
			buf.append(">");
		}
		return null;
	}

	/**
	 * Visit method for {@link ErrorType}.
	 * 
	 * @param t the error type to visit
	 * @param p unused parameter (can be {@code null})
	 * @return {@code null}
	 */
	@Override
	public Void visitError(ErrorType t, Void p) {
		buf.append(t.toString());
		return null;
	}

	/**
	 * Visit method for {@link TypeVariable}.
	 * 
	 * @param t the type variable to visit
	 * @param p unused parameter (can be {@code null})
	 * @return {@code null}
	 */
	@Override
	public Void visitTypeVariable(TypeVariable t, Void p) {
		buf.append(t.toString());
		TypeMirror lower = t.getLowerBound();
		if (lower.getKind() != TypeKind.NULL) {
			buf.append(" super ");
			visit(lower);
		}
		TypeMirror upper = t.getUpperBound();
		if (!upper.toString().equals("java.lang.Object")) {
			buf.append(" extends ");
			visit(upper);
		}
		return null;
	}

	/**
	 * Visit method for {@link WildcardType}.
	 * 
	 * @param t the wildcard type to visit
	 * @param p unused parameter (can be {@code null})
	 * @return {@code null}
	 */
	@Override
	public Void visitWildcard(WildcardType t, Void p) {
		buf.append("?");
		TypeMirror sup = t.getSuperBound();
		if (sup != null) {
			buf.append(" super ");
			visit(sup);
		}
		TypeMirror ext = t.getExtendsBound();
		if (ext != null) {
			buf.append(" extends ");
			visit(ext);
		}
		return null;
	}

	/**
	 * Visit method for {@link ExecutableType}.
	 * 
	 * @param t the executable type to visit
	 * @param p unused parameter (can be {@code null})
	 * @return {@code null}
	 */
	@Override
	public Void visitExecutable(ExecutableType t, Void p) {
		buf.append(t.toString());
		return null;
	}

	/**
	 * Visit method for {@link NoType}.
	 * 
	 * @param t the no-type to visit
	 * @param p unused parameter (can be {@code null})
	 * @return {@code null}
	 */
	@Override
	public Void visitNoType(NoType t, Void p) {
		buf.append(t.toString());
		return null;
	}

	/**
	 * Visit method for unknown {@link TypeMirror} instances.
	 * 
	 * @param t the unknown type to visit
	 * @param p unused parameter (can be {@code null})
	 * @return {@code null}
	 */
	@Override
	public Void visitUnknown(TypeMirror t, Void p) {
		buf.append(t.toString());
		return null;
	}

	/**
	 * Visit method for {@link UnionType}.
	 * 
	 * @param t the union type to visit
	 * @param p unused parameter (can be {@code null})
	 * @return {@code null}
	 */
	@Override
	public Void visitUnion(UnionType t, Void p) {
		Iterator<? extends TypeMirror> it = t.getAlternatives().iterator();
		if (it.hasNext()) {
			visit(it.next());
			while (it.hasNext()) {
				buf.append(" | ");
				visit(it.next());
			}
		}
		return null;
	}

	/**
	 * Visit method for {@link IntersectionType}.
	 * 
	 * @param t the intersection type to visit
	 * @param p unused parameter (can be {@code null})
	 * @return {@code null}
	 */
	@Override
	public Void visitIntersection(IntersectionType t, Void p) {
		Iterator<? extends TypeMirror> it = t.getBounds().iterator();
		if (it.hasNext()) {
			visit(it.next());
			while (it.hasNext()) {
				buf.append(" & ");
				visit(it.next());
			}
		}
		return null;
	}
}
