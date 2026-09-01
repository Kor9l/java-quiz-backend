package com.korl.javaquiz.practice;

import java.util.Map;
import java.util.Set;

/**
 * What submitted Java is allowed to touch.
 *
 * <p>An allowlist rather than a denylist, because the interesting attacks are the ones nobody
 * thought to list. A learner solving an exercise about collections, strings or streams needs a
 * handful of {@code java.*} packages and nothing else; a submission reaching for anything
 * outside them is either lost or probing, and both are better answered with a refusal than
 * with a stack trace.
 *
 * <p>The policy is read twice at two different depths — by {@link ClassFileGuard} over the
 * compiled constant pool, and again by {@link SandboxClassLoader} as classes are actually
 * resolved. The first gives a learner a message they can act on; the second is what makes the
 * refusal true, since it also covers whatever the first failed to think of.
 */
public final class SandboxPolicy {

    /**
     * Packages a submission may name, matched exactly rather than by prefix: allowing
     * {@code java.util} must not quietly allow {@code java.util.concurrent}.
     */
    private static final Set<String> ALLOWED_PACKAGES = Set.of(
            "java.lang",
            "java.math",
            "java.time",
            "java.time.format",
            "java.time.temporal",
            "java.util",
            "java.util.function",
            "java.util.regex",
            "java.util.stream");

    /**
     * Classes inside those packages that are refused anyway. Threads are here because a thread
     * a submission starts outlives the attempt that started it: the run is bounded by a timeout
     * on the calling thread, and nothing bounds the ones it spawned.
     */
    private static final Set<String> REFUSED_CLASSES = Set.of(
            "java.lang.Runtime",
            "java.lang.Process",
            "java.lang.ProcessBuilder",
            "java.lang.ProcessHandle",
            "java.lang.ClassLoader",
            "java.lang.SecurityManager",
            "java.lang.Thread",
            "java.lang.ThreadGroup",
            "java.lang.ThreadLocal",
            "java.lang.InheritableThreadLocal",
            "java.lang.Module",
            "java.lang.ModuleLayer",
            "java.lang.StackWalker",
            "java.util.ServiceLoader",
            "java.util.Timer",
            "java.util.TimerTask",
            "java.util.Scanner");

    /**
     * Classes allowed from outside {@link #ALLOWED_PACKAGES}. Every one of them is language
     * plumbing rather than API: a lambda links through {@code LambdaMetafactory}, string
     * concatenation links through {@code StringConcatFactory}, and a {@code record} gets its
     * {@code equals} from {@code ObjectMethods}. Refusing these would refuse ordinary Java, so
     * they are allowed as names and then held to {@link #ALLOWED_MEMBERS}, which grants each
     * one the bootstrap method the compiler calls and nothing else.
     */
    private static final Set<String> ALLOWED_CLASSES = Set.of(
            // The type of System.out, and so unavoidable the moment printing is allowed at all.
            "java.io.PrintStream",
            "java.io.Serializable",
            "java.lang.invoke.CallSite",
            "java.lang.invoke.LambdaMetafactory",
            "java.lang.invoke.MethodHandle",
            "java.lang.invoke.MethodHandles",
            "java.lang.invoke.MethodHandles$Lookup",
            "java.lang.invoke.MethodType",
            "java.lang.invoke.SerializedLambda",
            "java.lang.invoke.StringConcatFactory",
            "java.lang.invoke.TypeDescriptor",
            "java.lang.runtime.ObjectMethods");

    /**
     * Members a submission may use on a class that is too useful to refuse whole, or too
     * dangerous to allow whole. {@code System} is the case that forces this to exist:
     * {@code System.out} is the first thing a learner reaches for, and {@code System.exit}
     * would take the server down with it. An empty set means the class may be named — a
     * descriptor has to mention it — but that nothing on it may be called.
     */
    private static final Map<String, Set<String>> ALLOWED_MEMBERS = Map.ofEntries(
            // Printing and nothing else. Not the constructor, which takes a file name; not
            // close, which would shut the real stdout for the whole process.
            Map.entry("java.io.PrintStream", Set.of(
                    "print", "println", "printf", "format", "append", "flush")),
            Map.entry("java.io.Serializable", Set.of()),
            Map.entry("java.lang.System", Set.of(
                    "out", "err", "arraycopy", "currentTimeMillis", "nanoTime",
                    "lineSeparator", "identityHashCode")),
            Map.entry("java.lang.invoke.LambdaMetafactory", Set.of("metafactory", "altMetafactory")),
            Map.entry("java.lang.invoke.StringConcatFactory", Set.of("makeConcat", "makeConcatWithConstants")),
            Map.entry("java.lang.runtime.ObjectMethods", Set.of("bootstrap")),
            Map.entry("java.lang.invoke.CallSite", Set.of()),
            Map.entry("java.lang.invoke.MethodHandle", Set.of()),
            Map.entry("java.lang.invoke.MethodHandles", Set.of()),
            Map.entry("java.lang.invoke.MethodHandles$Lookup", Set.of()),
            Map.entry("java.lang.invoke.MethodType", Set.of()),
            Map.entry("java.lang.invoke.SerializedLambda", Set.of()),
            Map.entry("java.lang.invoke.TypeDescriptor", Set.of()));

    /**
     * Members refused on a class whose remaining surface is harmless. Reflection is most of
     * the list: it is how a submission would reach a class the policy never let it name.
     */
    private static final Map<String, Set<String>> REFUSED_MEMBERS = Map.of(
            "java.lang.Class", Set.of(
                    "forName", "newInstance", "getClassLoader", "getProtectionDomain",
                    "getResource", "getResourceAsStream", "getModule",
                    "getMethod", "getMethods", "getDeclaredMethod", "getDeclaredMethods",
                    "getField", "getFields", "getDeclaredField", "getDeclaredFields",
                    "getConstructor", "getConstructors",
                    "getDeclaredConstructor", "getDeclaredConstructors"),
            // Waiting on a monitor nothing will ever notify is a hang, and a hang is only
            // noticed by the timeout, several seconds of a shared machine later.
            "java.lang.Object", Set.of("wait", "notify", "notifyAll"));

    private SandboxPolicy() {
    }

    /** Whether a submission may name this class at all. Arrays and primitives are fine. */
    public static boolean allowsClass(String name) {
        String type = elementType(name);
        if (type == null) {
            return true;
        }
        if (REFUSED_CLASSES.contains(type)) {
            return false;
        }
        if (ALLOWED_CLASSES.contains(type)) {
            return true;
        }
        int lastDot = type.lastIndexOf('.');
        if (lastDot < 0) {
            // The default package, where only a submission's own classes live — and those are
            // resolved as sandbox classes before the policy is ever consulted.
            return false;
        }
        return ALLOWED_PACKAGES.contains(type.substring(0, lastDot));
    }

    /** Whether a submission may use one field or method of a class it is allowed to name. */
    public static boolean allowsMember(String owner, String member) {
        String type = elementType(owner);
        if (type == null) {
            return true;
        }
        Set<String> allowed = ALLOWED_MEMBERS.get(type);
        if (allowed != null) {
            return allowed.contains(member);
        }
        Set<String> refused = REFUSED_MEMBERS.get(type);
        return refused == null || !refused.contains(member);
    }

    /**
     * The class at the bottom of an array or field descriptor, or null when no reference type
     * is involved — a two-dimensional int array names no class and so has nothing to check.
     */
    private static String elementType(String name) {
        String type = name.replace('/', '.');
        while (type.startsWith("[")) {
            type = type.substring(1);
        }
        if (type.length() == 1 && "ZBCSIJFDV".indexOf(type.charAt(0)) >= 0) {
            return null;
        }
        if (type.length() > 2 && type.charAt(0) == 'L' && type.endsWith(";")) {
            type = type.substring(1, type.length() - 1);
        }
        return type.isEmpty() ? null : type;
    }
}
