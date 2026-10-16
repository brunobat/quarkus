package io.quarkus.opentelemetry.deployment;

import static io.quarkus.opentelemetry.deployment.RestClientCodeFunctionNameResolver.findInterfaceMethod;
import static io.quarkus.opentelemetry.deployment.RestClientCodeFunctionNameResolver.resolve;
import static io.quarkus.opentelemetry.deployment.RestClientCodeFunctionNameResolver.sameParameterTypes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class RestClientCodeFunctionNameResolverTest {

    private static Index index;

    @BeforeAll
    static void index() throws IOException {
        index = Index.of(BaseClient.class, HelloClient.class, HelloClientWrapper.class, OtherClient.class,
                MultiInterfaceWrapper.class, LeftClient.class, RightClient.class, DiamondClient.class, DiamondWrapper.class);
    }

    /**
     * Test the wrapperMethod helper.
     */
    @Test
    void wrapperMethodHelperSelectsTheExactMethod() {
        // no-arg overload: must come from the wrapper, not from the interface that also declares hello()
        MethodInfo noArgs = wrapperMethod(HelloClientWrapper.class, "hello");
        assertThat(noArgs.declaringClass().name()).isEqualTo(dotName(HelloClientWrapper.class));
        assertThat(noArgs.name()).isEqualTo("hello");
        assertThat(noArgs.parametersCount()).isZero();

        // String overload: overloads are told apart by parameter type, not only by name
        MethodInfo stringArg = wrapperMethod(HelloClientWrapper.class, "hello", String.class);
        assertThat(stringArg.declaringClass().name()).isEqualTo(dotName(HelloClientWrapper.class));
        assertThat(stringArg.name()).isEqualTo("hello");
        assertThat(stringArg.parametersCount()).isEqualTo(1);
        assertThat(stringArg.parameterType(0).name()).isEqualTo(dotName(String.class));

        // int overload: primitives are named by keyword in Jandex, same as Class#getName(), never boxed
        MethodInfo intArg = wrapperMethod(HelloClientWrapper.class, "hello", int.class);
        assertThat(intArg.parametersCount()).isEqualTo(1);
        assertThat(intArg.parameterType(0).name().toString()).isEqualTo("int");

        // generic parameter: List<String> is matched on its erasure, like the resolver does
        MethodInfo listArg = wrapperMethod(HelloClientWrapper.class, "list", List.class);
        assertThat(listArg.parameterType(0).name()).isEqualTo(dotName(List.class));

        // the overloads are different methods, so the helper is not always returning the first hello()
        assertThat(noArgs).isNotEqualTo(stringArg);
        assertThat(stringArg).isNotEqualTo(intArg);

        // no exact match must fail loudly rather than fall back to another overload or method
        assertThatThrownBy(() -> wrapperMethod(HelloClientWrapper.class, "hello", long.class))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wrapperMethod(HelloClientWrapper.class, "doesNotExist"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void methodDeclaredOnTheInterface() {
        assertThat(resolve(index, wrapperMethod(HelloClientWrapper.class, "hello", String.class)))
                .isEqualTo(HelloClient.class.getName() + ".hello");
    }

    @Test
    void methodDeclaredOnASuperInterface() {
        assertThat(resolve(index, wrapperMethod(HelloClientWrapper.class, "base", String.class)))
                .isEqualTo(BaseClient.class.getName() + ".base");
    }

    @Test
    void overloadsAreMatchedOnParameterTypes() {
        MethodInfo noArgs = wrapperMethod(HelloClientWrapper.class, "hello");
        MethodInfo stringArg = wrapperMethod(HelloClientWrapper.class, "hello", String.class);
        MethodInfo intArg = wrapperMethod(HelloClientWrapper.class, "hello", int.class);

        MethodInfo helloNoArgs = findInterfaceMethod(index, dotName(HelloClient.class), noArgs);
        MethodInfo helloString = findInterfaceMethod(index, dotName(HelloClient.class), stringArg);
        MethodInfo helloInt = findInterfaceMethod(index, dotName(HelloClient.class), intArg);

        assertThat(helloNoArgs.parametersCount()).isZero();
        assertThat(helloString.parameterType(0).name()).isEqualTo(dotName(String.class));
        assertThat(helloInt.parameterType(0).name().toString()).isEqualTo("int");
    }

    @Test
    void nestedInterfaceNameUsesBinaryName() {
        // the generated wrapper of a nested interface must report the same name as Class#getName()
        assertThat(resolve(index, wrapperMethod(HelloClientWrapper.class, "hello", String.class)))
                .startsWith(getClass().getName() + "$HelloClient.");
    }

    @Test
    void methodNotDeclaredByAnyInterfaceFallsBackToTheWrapper() {
        assertThat(resolve(index, wrapperMethod(HelloClientWrapper.class, "extra")))
                .isEqualTo(HelloClientWrapper.class.getName() + ".extra");
    }

    @Test
    void interfaceMissingFromTheIndexFallsBackToTheWrapper() throws IOException {
        Index withoutInterfaces = Index.of(HelloClientWrapper.class);
        MethodInfo method = withoutInterfaces.getClassByName(dotName(HelloClientWrapper.class))
                .method("hello", org.jboss.jandex.Type.create(dotName(String.class), org.jboss.jandex.Type.Kind.CLASS));

        assertThat(resolve(withoutInterfaces, method)).isEqualTo(HelloClientWrapper.class.getName() + ".hello");
        assertThat(findInterfaceMethod(withoutInterfaces, dotName(HelloClient.class), method)).isNull();
    }

    @Test
    void firstInterfaceDeclaringTheMethodWins() {
        // both interfaces declare ping(), the wrapper lists HelloClient first
        assertThat(resolve(index, wrapperMethod(MultiInterfaceWrapper.class, "ping")))
                .isEqualTo(HelloClient.class.getName() + ".ping");
        // only OtherClient declares pong()
        assertThat(resolve(index, wrapperMethod(MultiInterfaceWrapper.class, "pong")))
                .isEqualTo(OtherClient.class.getName() + ".pong");
    }

    @Test
    void diamondHierarchyIsResolved() {
        // Diamond: Left and Right both extend Base, DiamondClient extends both
        assertThat(resolve(index, wrapperMethod(DiamondWrapper.class, "base", String.class)))
                .isEqualTo(BaseClient.class.getName() + ".base");
        assertThat(resolve(index, wrapperMethod(DiamondWrapper.class, "right")))
                .isEqualTo(RightClient.class.getName() + ".right");
    }

    @Test
    void cyclicInterfaceHierarchyTerminates() throws IOException {
        // javac refuses cyclic interfaces, so build them with ASM and index the bytes without loading them:
        // Wrapper implements CycleA, CycleA extends CycleB, CycleB extends CycleA; none declares the method
        Indexer indexer = new Indexer();
        indexer.index(new ByteArrayInputStream(interfaceBytes("cycle/CycleA", "cycle/CycleB")));
        indexer.index(new ByteArrayInputStream(interfaceBytes("cycle/CycleB", "cycle/CycleA")));
        indexer.index(new ByteArrayInputStream(classBytes("cycle/Wrapper", "cycle/CycleA", "missing")));
        Index cyclic = indexer.complete();
        MethodInfo missing = cyclic.getClassByName(DotName.createSimple("cycle.Wrapper")).firstMethod("missing");

        assertThat(findInterfaceMethod(cyclic, DotName.createSimple("cycle.CycleA"), missing)).isNull();
        assertThat(resolve(cyclic, missing)).isEqualTo("cycle.Wrapper.missing");
    }

    @Test
    void cyclicInterfaceHierarchyStillFindsTheMethod() throws IOException {
        // same cycle, but CycleB declares the method
        Indexer indexer = new Indexer();
        indexer.index(new ByteArrayInputStream(interfaceBytes("cycle/CycleA", "cycle/CycleB")));
        indexer.index(new ByteArrayInputStream(interfaceBytes("cycle/CycleB", "cycle/CycleA", "found")));
        indexer.index(new ByteArrayInputStream(classBytes("cycle/Wrapper", "cycle/CycleA", "found")));
        Index cyclic = indexer.complete();
        MethodInfo found = cyclic.getClassByName(DotName.createSimple("cycle.Wrapper")).firstMethod("found");

        assertThat(resolve(cyclic, found)).isEqualTo("cycle.CycleB.found");
    }

    @Test
    void sameParameterTypesComparesCountAndErasedTypes() {
        MethodInfo noArgs = wrapperMethod(HelloClientWrapper.class, "hello");
        MethodInfo stringArg = wrapperMethod(HelloClientWrapper.class, "hello", String.class);
        MethodInfo intArg = wrapperMethod(HelloClientWrapper.class, "hello", int.class);
        MethodInfo listArg = wrapperMethod(HelloClientWrapper.class, "list", List.class);
        MethodInfo listOnInterface = index.getClassByName(dotName(HelloClient.class)).firstMethod("list");

        assertThat(sameParameterTypes(stringArg, stringArg)).isTrue();
        assertThat(sameParameterTypes(noArgs, stringArg)).isFalse();
        assertThat(sameParameterTypes(stringArg, intArg)).isFalse();
        // List<String> on the wrapper versus List<String> on the interface, compared by erasure
        assertThat(sameParameterTypes(listArg, listOnInterface)).isTrue();
    }

    private static byte[] interfaceBytes(String internalName, String superInterface, String... methodNames) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, internalName, null,
                "java/lang/Object", new String[] { superInterface });
        for (String methodName : methodNames) {
            writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, methodName, "()Ljava/lang/String;", null, null)
                    .visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classBytes(String internalName, String implementedInterface, String... methodNames) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object",
                new String[] { implementedInterface });
        for (String methodName : methodNames) {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()Ljava/lang/String;", null, null);
            method.visitCode();
            method.visitInsn(Opcodes.ACONST_NULL);
            method.visitInsn(Opcodes.ARETURN);
            method.visitMaxs(1, 1);
            method.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static MethodInfo wrapperMethod(Class<?> wrapper, String name, Class<?>... parameterTypes) {
        ClassInfo classInfo = index.getClassByName(dotName(wrapper));
        for (MethodInfo method : classInfo.methods()) {
            if (!method.name().equals(name) || method.parametersCount() != parameterTypes.length) {
                continue;
            }
            boolean match = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (!method.parameterType(i).name().equals(dotName(parameterTypes[i]))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return method;
            }
        }
        throw new IllegalArgumentException("No method " + name + " on " + wrapper);
    }

    private static DotName dotName(Class<?> clazz) {
        return DotName.createSimple(clazz.getName());
    }

    interface BaseClient {
        String base(String value);
    }

    interface HelloClient extends BaseClient {
        String hello();

        String hello(String query);

        String hello(int value);

        String list(List<String> values);

        String ping();
    }

    interface OtherClient {
        String ping();

        String pong();
    }

    // mimics the shape of the generated $$CDIWrapper: a class implementing the REST client interface
    static class HelloClientWrapper implements HelloClient {
        @Override
        public String base(String value) {
            return value;
        }

        @Override
        public String hello() {
            return "";
        }

        @Override
        public String hello(String query) {
            return query;
        }

        @Override
        public String hello(int value) {
            return "";
        }

        @Override
        public String list(List<String> values) {
            return "";
        }

        @Override
        public String ping() {
            return "";
        }

        public String extra() {
            return "";
        }
    }

    interface LeftClient extends BaseClient {
    }

    interface RightClient extends BaseClient {
        String right();
    }

    interface DiamondClient extends LeftClient, RightClient {
    }

    static class DiamondWrapper implements DiamondClient {
        @Override
        public String base(String value) {
            return value;
        }

        @Override
        public String right() {
            return "";
        }
    }

    static class MultiInterfaceWrapper implements HelloClient, OtherClient {
        @Override
        public String base(String value) {
            return value;
        }

        @Override
        public String hello() {
            return "";
        }

        @Override
        public String hello(String query) {
            return query;
        }

        @Override
        public String hello(int value) {
            return "";
        }

        @Override
        public String list(List<String> values) {
            return "";
        }

        @Override
        public String ping() {
            return "";
        }

        @Override
        public String pong() {
            return "";
        }
    }
}
