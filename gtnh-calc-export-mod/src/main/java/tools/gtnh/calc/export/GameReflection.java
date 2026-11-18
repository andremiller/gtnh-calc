package tools.gtnh.calc.export;

import java.lang.reflect.*;
import java.util.*;

/** Narrow adapters for optional mods: never reflect over obfuscated Minecraft members. */
final class GameReflection {
    private record CallKey(Class<?> type, String name, List<Class<?>> arguments) {}
    private record FieldKey(Class<?> type, String name) {}
    private static final Map<CallKey, Method> METHODS = new HashMap<>();
    private static final Map<FieldKey, Field> FIELDS = new HashMap<>();

    static boolean isKind(Object object, String name) {
        for (Class<?> c = object.getClass(); c != null; c = c.getSuperclass()) if (c.getName().equals(name)) return true;
        return false;
    }
    static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name); }
    static Object field(Object target, String name) throws ReflectiveOperationException {
        Class<?> type = target instanceof Class<?> c ? c : target.getClass();
        FieldKey key = new FieldKey(type, name); Field field = FIELDS.get(key);
        if (field == null) { field = type.getField(name); field.setAccessible(true); FIELDS.put(key, field); }
        return field.get(target instanceof Class<?> ? null : target);
    }
    static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        Class<?> type = target instanceof Class<?> c ? c : target.getClass();
        List<Class<?>> argumentTypes = new ArrayList<>();
        for (Object argument : args) argumentTypes.add(argument == null ? Void.class : argument.getClass());
        CallKey key = new CallKey(type, name, argumentTypes);
        Method cached = METHODS.get(key);
        if (cached != null) return invoke(cached, target, args);
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            boolean compatible = true; Class<?>[] parameters = method.getParameterTypes();
            for (int i = 0; i < args.length; i++) {
                Class<?> expected = parameters[i];
                if (expected == int.class) expected = Integer.class;
                if (expected == long.class) expected = Long.class;
                if (expected == boolean.class) expected = Boolean.class;
                if (args[i] != null && !expected.isInstance(args[i])) compatible = false;
            }
            if (compatible) {
                method.setAccessible(true); METHODS.put(key, method);
                return invoke(method, target, args);
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
    private static Object invoke(Method method, Object target, Object[] args) throws ReflectiveOperationException {
        try { return method.invoke(target instanceof Class<?> ? null : target, args); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Error fatal) throw fatal;
            throw error;
        }
    }
    static List<?> list(Object value) {
        if (value == null) return List.of();
        if (value instanceof Collection<?> c) return new ArrayList<>(c);
        if (value.getClass().isArray()) {
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) result.add(Array.get(value, i));
            return result;
        }
        throw new IllegalArgumentException("Expected collection: " + value.getClass());
    }
}
