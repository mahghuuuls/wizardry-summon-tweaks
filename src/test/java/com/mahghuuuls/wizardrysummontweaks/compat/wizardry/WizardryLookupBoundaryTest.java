package com.mahghuuuls.wizardrysummontweaks.compat.wizardry;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WizardryLookupBoundaryTest {
    @Test void compiledObservationNeverCallsWizardrysLoadedEntityScanningLookup() throws Exception {
        List<String> scanningCalls = new ArrayList<>();
        try (InputStream input = getClass().getResourceAsStream("/com/mahghuuuls/wizardrysummontweaks/compat/wizardry/WizardrySummonProvider.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                            // Both pinned Wizardry methods linearly scan World.loadedEntityList.
                            if ((owner.equals("electroblob/wizardry/entity/living/ISummonedCreature") && method.equals("getCaster"))
                                    || (owner.equals("electroblob/wizardry/util/EntityUtils") && method.equals("getEntityByUUID"))) {
                                scanningCalls.add(owner + "." + method);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(scanningCalls.isEmpty(), "Per-summon observation must not scan loaded entities: " + scanningCalls);
    }
}
