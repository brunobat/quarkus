package io.quarkus.opentelemetry.deployment;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;

/**
 * Resolves the {@code code.function.name} to report for a method of the {@code $$CDIWrapper} generated for a REST
 * client interface: the name of the interface method the wrapper method implements.
 */
final class RestClientCodeFunctionNameResolver {

    private RestClientCodeFunctionNameResolver() {
    }

    /**
     * @return {@code <declaring interface>.<method name>} of the interface method implemented by the given wrapper
     *         method, or {@code <wrapper class>.<method name>} when no interface of the wrapper declares it
     */
    static String resolve(IndexView index, MethodInfo wrapperMethod) {
        ClassInfo wrapper = wrapperMethod.declaringClass();
        for (DotName interfaceName : wrapper.interfaceNames()) {
            MethodInfo interfaceMethod = findInterfaceMethod(index, interfaceName, wrapperMethod);
            if (interfaceMethod != null) {
                return interfaceMethod.declaringClass().name().toString() + "." + interfaceMethod.name();
            }
        }
        // the method is not declared by any interface we know of, report the wrapper itself
        return wrapper.name().toString() + "." + wrapperMethod.name();
    }

    /**
     * Searches the given interface and, depth first, its super interfaces for a method with the same name and
     * parameter types as {@code method}.
     * <p>
     * Every interface is visited at most once.
     */
    static MethodInfo findInterfaceMethod(IndexView index, DotName interfaceName, MethodInfo method) {
        Set<DotName> visited = new HashSet<>();
        Deque<DotName> toVisit = new ArrayDeque<>();
        toVisit.push(interfaceName);
        while (!toVisit.isEmpty()) {
            DotName current = toVisit.pop();
            if (!visited.add(current)) {
                continue;
            }
            ClassInfo iface = index.getClassByName(current);
            if (iface == null) {
                continue;
            }
            for (MethodInfo candidate : iface.methods()) {
                if (candidate.name().equals(method.name()) && sameParameterTypes(candidate, method)) {
                    return candidate;
                }
            }
            // push in reverse order so super interfaces are visited depth first, in declaration order
            for (int i = iface.interfaceNames().size() - 1; i >= 0; i--) {
                DotName superInterface = iface.interfaceNames().get(i);
                if (!visited.contains(superInterface)) {
                    toVisit.push(superInterface);
                }
            }
        }
        return null;
    }

    /**
     * Compares the erased parameter types of two methods.
     */
    static boolean sameParameterTypes(MethodInfo a, MethodInfo b) {
        if (a.parametersCount() != b.parametersCount()) {
            return false;
        }
        for (int i = 0; i < a.parametersCount(); i++) {
            if (!a.parameterType(i).name().equals(b.parameterType(i).name())) {
                return false;
            }
        }
        return true;
    }
}
