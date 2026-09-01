package com.korl.javaquiz.practice;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@link SandboxPolicy} out of compiled bytecode, before any of it is loaded.
 *
 * <p>The middle of three rings. It walks the constant pool, which is where a class writes down
 * every other class it names and every field and method it calls: a submission cannot reach
 * {@code Runtime.getRuntime()} without a {@code Methodref} to it sitting in the pool, whatever
 * the source looked like. That is what makes this stronger than reading the source —
 * fully-qualified names, string tricks and comments all collapse to the same handful of
 * entries by the time the compiler is finished.
 *
 * <p>It is deliberately not the last word. {@link SandboxClassLoader} re-checks every class as
 * it is actually resolved, so a reference this parser failed to understand still cannot load.
 */
public final class ClassFileGuard {

    private static final int CONSTANT_UTF8 = 1;
    private static final int CONSTANT_INTEGER = 3;
    private static final int CONSTANT_FLOAT = 4;
    private static final int CONSTANT_LONG = 5;
    private static final int CONSTANT_DOUBLE = 6;
    private static final int CONSTANT_CLASS = 7;
    private static final int CONSTANT_STRING = 8;
    private static final int CONSTANT_FIELDREF = 9;
    private static final int CONSTANT_METHODREF = 10;
    private static final int CONSTANT_INTERFACE_METHODREF = 11;
    private static final int CONSTANT_NAME_AND_TYPE = 12;
    private static final int CONSTANT_METHOD_HANDLE = 15;
    private static final int CONSTANT_METHOD_TYPE = 16;
    private static final int CONSTANT_DYNAMIC = 17;
    private static final int CONSTANT_INVOKE_DYNAMIC = 18;
    private static final int CONSTANT_MODULE = 19;
    private static final int CONSTANT_PACKAGE = 20;

    private ClassFileGuard() {
    }

    /**
     * Checks every class a submission compiled to.
     *
     * @param classes  compiled bytecode by binary name, the submission's own classes included
     * @param ownNames the names in {@code classes}, which reference each other freely
     * @throws PracticeSubmissionException on the first reference the policy refuses
     */
    public static void check(Map<String, byte[]> classes, Set<String> ownNames) {
        for (byte[] bytecode : classes.values()) {
            check(bytecode, ownNames);
        }
    }

    private static void check(byte[] bytecode, Set<String> ownNames) {
        Pool pool = parse(bytecode);
        for (String named : pool.namedClasses()) {
            refuseUnless(ownNames.contains(named) || SandboxPolicy.allowsClass(named),
                    "practice.error.forbiddenClass", named);
        }
        for (String descriptor : pool.descriptors()) {
            for (String type : typesIn(descriptor)) {
                refuseUnless(ownNames.contains(type) || SandboxPolicy.allowsClass(type),
                        "practice.error.forbiddenClass", type);
            }
        }
        for (Reference reference : pool.references()) {
            if (ownNames.contains(reference.owner())) {
                continue;
            }
            refuseUnless(SandboxPolicy.allowsClass(reference.owner()),
                    "practice.error.forbiddenClass", reference.owner());
            refuseUnless(SandboxPolicy.allowsMember(reference.owner(), reference.member()),
                    "practice.error.forbiddenMember", reference.owner() + "." + reference.member());
        }
    }

    private static void refuseUnless(boolean allowed, String messageKey, String detail) {
        if (!allowed) {
            throw new PracticeSubmissionException(SubmissionStatus.POLICY_ERROR, messageKey, detail);
        }
    }

    /**
     * Every reference type mentioned in a field or method descriptor. Parameter and return
     * types often appear nowhere else in the pool, so skipping descriptors would leave a hole.
     */
    static List<String> typesIn(String descriptor) {
        List<String> types = new ArrayList<>();
        int at = descriptor.indexOf('L');
        while (at >= 0) {
            int end = descriptor.indexOf(';', at);
            if (end < 0) {
                break;
            }
            types.add(descriptor.substring(at + 1, end).replace('/', '.'));
            at = descriptor.indexOf('L', end);
        }
        return types;
    }

    private static Pool parse(byte[] bytecode) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytecode))) {
            if (in.readInt() != 0xCAFEBABE) {
                throw new IllegalStateException("Not a class file");
            }
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            String[] utf8 = new String[count];
            int[] classNameIndex = new int[count];
            int[][] nameAndType = new int[count][];
            List<int[]> refs = new ArrayList<>();
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case CONSTANT_UTF8 -> utf8[i] = in.readUTF();
                    case CONSTANT_CLASS, CONSTANT_STRING, CONSTANT_METHOD_TYPE,
                            CONSTANT_MODULE, CONSTANT_PACKAGE -> {
                        int index = in.readUnsignedShort();
                        if (tag == CONSTANT_CLASS) {
                            classNameIndex[i] = index;
                        }
                    }
                    case CONSTANT_FIELDREF, CONSTANT_METHODREF, CONSTANT_INTERFACE_METHODREF ->
                            refs.add(new int[] {in.readUnsignedShort(), in.readUnsignedShort()});
                    case CONSTANT_NAME_AND_TYPE ->
                            nameAndType[i] = new int[] {in.readUnsignedShort(), in.readUnsignedShort()};
                    case CONSTANT_INTEGER, CONSTANT_FLOAT -> in.readInt();
                    case CONSTANT_LONG, CONSTANT_DOUBLE -> {
                        in.readLong();
                        // Eight-byte constants take two pool slots, and the second one is
                        // unusable rather than absent — skipping it keeps the indices aligned.
                        i++;
                    }
                    case CONSTANT_METHOD_HANDLE -> {
                        in.readUnsignedByte();
                        in.readUnsignedShort();
                    }
                    case CONSTANT_DYNAMIC, CONSTANT_INVOKE_DYNAMIC -> {
                        in.readUnsignedShort();
                        in.readUnsignedShort();
                    }
                    default -> throw new IllegalStateException("Unknown constant pool tag " + tag);
                }
            }
            return resolve(utf8, classNameIndex, nameAndType, refs);
        } catch (IOException | RuntimeException e) {
            // A class file this parser cannot read is not a class file this application wrote.
            throw new PracticeSubmissionException(
                    SubmissionStatus.POLICY_ERROR, "practice.error.unreadableClass", e.getMessage());
        }
    }

    private static Pool resolve(String[] utf8, int[] classNameIndex, int[][] nameAndType, List<int[]> refs) {
        List<String> namedClasses = new ArrayList<>();
        for (int i = 1; i < classNameIndex.length; i++) {
            if (classNameIndex[i] > 0) {
                namedClasses.add(utf8[classNameIndex[i]].replace('/', '.'));
            }
        }
        List<String> descriptors = new ArrayList<>();
        for (int[] pair : nameAndType) {
            if (pair != null) {
                descriptors.add(utf8[pair[1]]);
            }
        }
        List<Reference> references = new ArrayList<>();
        for (int[] ref : refs) {
            String owner = utf8[classNameIndex[ref[0]]].replace('/', '.');
            references.add(new Reference(owner, utf8[nameAndType[ref[1]][0]]));
        }
        return new Pool(namedClasses, descriptors, references);
    }

    private record Pool(List<String> namedClasses, List<String> descriptors, List<Reference> references) {
    }

    private record Reference(String owner, String member) {
    }
}
