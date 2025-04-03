package comp0012.main;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.TargetLostException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

public class ConstantFolder
{
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	public ConstantFolder(String classFilePath)
	{
		try {
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	// Separate LDC and LDC2_W.
	private Number getConstantValue(InstructionHandle handle, ConstantPoolGen constPoolGen) {
		Instruction ins = handle.getInstruction();
		if (ins instanceof LDC) {
			return (Number) ((LDC) ins).getValue(constPoolGen); // Could be Integer or Float.
		} else if (ins instanceof LDC2_W) {
			return (Number) ((LDC2_W) ins).getValue(constPoolGen); // Could be Long or Double.
		}
		return null;
	}

	private Number computeArithmetic(Number v1, Number v2, short opcode) {
		switch (opcode) {
			case Constants.IADD:
				return v1.intValue() + v2.intValue();
			case Constants.ISUB:
				return v1.intValue() - v2.intValue();
			case Constants.IMUL:
				return v1.intValue() * v2.intValue();
			case Constants.IDIV:
				if (v2.intValue() == 0) {
					return null;
				}
				return v1.intValue() / v2.intValue();

			case Constants.FADD:
				return v1.floatValue() + v2.floatValue();
			case Constants.FSUB:
				return v1.floatValue() - v2.floatValue();
			case Constants.FMUL:
				return v1.floatValue() * v2.floatValue();
			case Constants.FDIV:
				if (v2.floatValue() == 0.00f) {
					return null;
				}
				return v1.floatValue() / v2.floatValue();

			case Constants.LADD:
				return v1.longValue() + v2.longValue();
			case Constants.LSUB:
				return v1.longValue() - v2.longValue();
			case Constants.LMUL:
				return v1.longValue() * v2.longValue();
			case Constants.LDIV:
				if (v2.longValue() == 0L) {
					return null;
				}
				return v1.longValue() / v2.longValue();

			case Constants.DADD:
				return v1.doubleValue() + v2.doubleValue();
			case Constants.DSUB:
				return v1.doubleValue() - v2.doubleValue();
			case Constants.DMUL:
				return v1.doubleValue() * v2.doubleValue();
			case Constants.DDIV:
				if (v2.doubleValue() == 0.00) {
					return null;
				}
				return v1.doubleValue() / v2.doubleValue();

			default:
				return null;
		}
	}

	// Distinguish the numeric type, return related instructions.
	private Instruction createFoldingInstruction(Number result, ConstantPoolGen constPoolGen) {
		if (result instanceof Integer) {
			return new LDC(constPoolGen.addInteger((Integer) result));
		}
		else if (result instanceof Float) {
			return new LDC(constPoolGen.addFloat((Float) result));
		}
		else if (result instanceof Long) {
			return new LDC2_W(constPoolGen.addLong((Long) result));
		}
		else if (result instanceof Double) {
			return new LDC2_W(constPoolGen.addDouble((Double) result));
		}
		else {
			return null;
		}
	}

	public void optimize()
	{
		ClassGen clsGen = new ClassGen(original);
		ConstantPoolGen constPoolGen = clsGen.getConstantPool();

		for (Method method : clsGen.getMethods()) {
			MethodGen methGen = new MethodGen(method, clsGen.getClassName(), constPoolGen);
			InstructionList insList = methGen.getInstructionList();

			InstructionFinder finder = new InstructionFinder(insList);
			String pattern =
					"(LDC|LDC2_W) (LDC|LDC2_W) " +
							"(IADD|ISUB|IMUL|IDIV|" +
							" FADD|FSUB|FMUL|FDIV|" +
							" LADD|LSUB|LMUL|LDIV|" +
							" DADD|DSUB|DMUL|DDIV)";

			// Pattern matching.
			for (Iterator<?> i = finder.search(pattern); i.hasNext();) {
				InstructionHandle[] match = (InstructionHandle[]) i.next();
				InstructionHandle handle1 = match[0];
				InstructionHandle handle2 = match[1];
				InstructionHandle opHandle = match[2];

				Number val1 = getConstantValue(handle1, constPoolGen);
				Number val2 = getConstantValue(handle2, constPoolGen);

				ArithmeticInstruction op = (ArithmeticInstruction) opHandle.getInstruction();
				short opcode = op.getOpcode();

				Number result = computeArithmetic(val1, val2, opcode);
				if (result == null) {
					continue;
				}

				// Insert the new instruction and delete old ones.
				Instruction newInst = createFoldingInstruction(result, constPoolGen);
				InstructionHandle newHandle = insList.insert(handle1, newInst);
				try {
					insList.delete(handle1, opHandle);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
			}

			methGen.setMaxStack();
			clsGen.replaceMethod(method, methGen.getMethod());
		}

		this.optimized = clsGen.getJavaClass();
	}

	public void write(String optimisedFilePath)
	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
			this.optimized.dump(out);
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
