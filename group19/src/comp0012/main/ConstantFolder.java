package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;
import org.apache.bcel.classfile.*;

public class ConstantFolder
{
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	public ConstantFolder(String classFilePath)
	{
		try{
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
			case Constants.IREM:
				if (v2.intValue() == 0) {
					return null;
				}
				return v1.intValue() % v2.intValue();

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
			case Constants.FREM:
				if (v2.floatValue() == 0.00f) {
					return null;
				}
				return v1.floatValue() % v2.floatValue();

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
			case Constants.LREM:
				if (v2.longValue() == 0L) {
					return null;
				}
				return v1.longValue() % v2.longValue();

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
			case Constants.DREM:
				if (v2.doubleValue() == 0.00) {
					return null;
				}
				return v1.doubleValue() % v2.doubleValue();

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

	public void optimize() {
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();

		// Repeatedly optimize until no more changes can be made
		boolean classModified;

		do {
			classModified = false;
			Method[] methods = cgen.getMethods();

			for (int methodIndex = 0; methodIndex < methods.length; methodIndex++) {
				Method method = methods[methodIndex];
				Code code = method.getCode();

				if (code != null) {
					MethodGen methodGen = new MethodGen(method, cgen.getClassName(), cpgen);
					InstructionList instructionList = methodGen.getInstructionList();

					if (instructionList != null && !instructionList.isEmpty()) {
						boolean methodModified = false;

						// TASK 1: SIMPLE FOLDING
						methodModified |= performSimpleFolding(instructionList, cpgen);

						// TASK 2: CONSTANT VARIABLES
						Map<Integer, Number> constantVariables = findConstantVariables(instructionList, cpgen);
						if (!constantVariables.isEmpty()) {
							methodModified |= optimizeConstantVariables(instructionList, cpgen, constantVariables);
						}

						// TASK 3: DYNAMIC VARIABLES
						methodModified |= optimizeDynamicVariables(instructionList, cpgen);

						if (methodModified) {
							instructionList.setPositions();
							methodGen.setMaxStack();
							methodGen.setMaxLocals();
							Method newMethod = methodGen.getMethod();
							cgen.replaceMethod(method, newMethod);
							classModified = true;
						}
					}
				}
			}
		} while (classModified);

		this.gen = cgen;
		this.optimized = cgen.getJavaClass();
	}

	// TASK 1: SIMPLE FOLDING IMPLEMENTATION
	private boolean performSimpleFolding(InstructionList insList, ConstantPoolGen cpgen) {
		boolean modified = false;

		InstructionFinder finder = new InstructionFinder(insList);
		String pattern =
				"(LDC|LDC2_W) (LDC|LDC2_W) " +
						"(IADD|ISUB|IMUL|IDIV|IREM|" +
						" FADD|FSUB|FMUL|FDIV|FREM|" +
						" LADD|LSUB|LMUL|LDIV|LREM|" +
						" DADD|DSUB|DMUL|DDIV|DREM)";

		// Pattern matching.
		for (Iterator<?> i = finder.search(pattern); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			InstructionHandle handle1 = match[0];
			InstructionHandle handle2 = match[1];
			InstructionHandle opHandle = match[2];

			Number val1 = getConstantValue(handle1, cpgen);
			Number val2 = getConstantValue(handle2, cpgen);

			ArithmeticInstruction op = (ArithmeticInstruction) opHandle.getInstruction();
			short opcode = op.getOpcode();

			Number result = computeArithmetic(val1, val2, opcode);
			if (result == null) {
				continue;
			}

			// Insert the new instruction and delete old ones.
			Instruction newInst = createFoldingInstruction(result, cpgen);
			InstructionHandle newHandle = insList.insert(handle1, newInst);
			try {
				insList.delete(handle1, opHandle);
				modified = true;
			} catch (TargetLostException e) {
				for (InstructionHandle target : e.getTargets()) {
					for (InstructionTargeter targeter : target.getTargeters()) {
						targeter.updateTarget(target, newHandle);
					}
				}
			}
		}

		return modified;
	}

	// TASK 2: CONSTANT VARIABLES METHODS

	private Map<Integer, Number> findConstantVariables(InstructionList instructionList, ConstantPoolGen cpgen) {
		Map<Integer, Number> constantVars = new HashMap<>();
		Map<Integer, Boolean> modifiedVars = new HashMap<>();

		// First pass: find variables that are assigned only once
		InstructionHandle handle = instructionList.getStart();
		while (handle != null) {
			Instruction instruction = handle.getInstruction();

			if (instruction instanceof StoreInstruction) {
				int index = ((StoreInstruction) instruction).getIndex();

				if (modifiedVars.containsKey(index)) {
					modifiedVars.put(index, true); // Variable is modified more than once
				} else {
					modifiedVars.put(index, false); // First assignment
				}
			}

			handle = handle.getNext();
		}

		// Second pass: find constant values for variables that are assigned only once
		handle = instructionList.getStart();
		while (handle != null) {
			Instruction instruction = handle.getInstruction();
			InstructionHandle next = handle.getNext();

			// Check for constant assignments - push constant followed by store
			if (next != null && next.getInstruction() instanceof StoreInstruction) {
				StoreInstruction store = (StoreInstruction) next.getInstruction();
				int index = store.getIndex();

				// Only consider variables that arent modified after initialisation
				if (modifiedVars.containsKey(index) && !modifiedVars.get(index)) {
					Number value = null;

					// Check all possible constant push instructions
					if (instruction instanceof LDC) {
						Object val = ((LDC) instruction).getValue(cpgen);
						if (val instanceof Number) {
							value = (Number) val;
						}
					} else if (instruction instanceof LDC2_W) {
						Object val = ((LDC2_W) instruction).getValue(cpgen);
						if (val instanceof Number) {
							value = (Number) val;
						}
					} else if (instruction instanceof BIPUSH) {
						value = ((BIPUSH) instruction).getValue();
					} else if (instruction instanceof SIPUSH) {
						value = ((SIPUSH) instruction).getValue();
					} else if (instruction instanceof ICONST) {
						value = ((ICONST) instruction).getValue();
					} else if (instruction instanceof FCONST) {
						value = ((FCONST) instruction).getValue();
					} else if (instruction instanceof LCONST) {
						value = ((LCONST) instruction).getValue();
					} else if (instruction instanceof DCONST) {
						value = ((DCONST) instruction).getValue();
					}

					if (value != null) {
						constantVars.put(index, value);
					}
				}
			}

			handle = next;
		}

		return constantVars;
	}

	private boolean optimizeConstantVariables(InstructionList instructionList, ConstantPoolGen cpgen,
											 Map<Integer, Number> constantVars) {
		// Check if method has branch instructions
		for (InstructionHandle handle = instructionList.getStart(); handle != null; handle = handle.getNext()) {
			if (handle.getInstruction() instanceof BranchInstruction) {
				// Skip methods with branches for now
				return false;
			}
		}

		boolean modified = false;
		InstructionHandle handle = instructionList.getStart();

		while (handle != null) {
			InstructionHandle next = handle.getNext();

			if (handle.getInstruction() instanceof LoadInstruction) {
				LoadInstruction load = (LoadInstruction) handle.getInstruction();
				int index = load.getIndex();
				
				if (constantVars.containsKey(index)) {
					Number value = constantVars.get(index);
					Instruction newInst = null;
					
					if (value instanceof Integer) {
						newInst = new LDC(cpgen.addInteger(value.intValue()));
					} else if (value instanceof Float) {
						newInst = new LDC(cpgen.addFloat(value.floatValue()));
					} else if (value instanceof Long) {
						newInst = new LDC2_W(cpgen.addLong(value.longValue()));
					} else if (value instanceof Double) {
						newInst = new LDC2_W(cpgen.addDouble(value.doubleValue()));
					}
					
					if (newInst != null) {
						InstructionHandle newHandle = instructionList.insert(handle, newInst);
						
						try {
							instructionList.delete(handle);
							modified = true;
							
							if (handle.hasTargeters()) {
								for (InstructionTargeter targeter : handle.getTargeters()) {
									targeter.updateTarget(handle, newHandle);
								}
							}
							
							handle = newHandle;
						} catch (TargetLostException e) {
							for (InstructionHandle target : e.getTargets()) {
								for (InstructionTargeter targeter : target.getTargeters()) {
									targeter.updateTarget(target, newHandle);
								}
							}
						}
					}
				}
			}
			
			handle = next;
		}
		
		if (modified) {
			instructionList.setPositions(true);
		}
		
		return modified;
	}

	// TASK 3: DYNAMIC VARIABLES IMPLEMENTATION
	private boolean optimizeDynamicVariables(InstructionList instructionList, ConstantPoolGen cpgen) {
		boolean modified = false;
		Map<Integer, Number> currentConstants = new HashMap<>();
		Set<Integer> comparisonVariables = new HashSet<>();

		for (InstructionHandle h = instructionList.getStart(); h != null; h = h.getNext()) {
			Instruction inst = h.getInstruction();
			if (inst instanceof IfInstruction) {
				short opcode = ((IfInstruction) inst).getOpcode();
				if (opcode >= Constants.IF_ICMPEQ && opcode <= Constants.IF_ICMPLE) {
					InstructionHandle prev1 = h.getPrev();
					InstructionHandle prev2 = (prev1 != null) ? prev1.getPrev() : null;

					if (prev1 != null && prev1.getInstruction() instanceof LoadInstruction) {
						comparisonVariables.add(((LoadInstruction) prev1.getInstruction()).getIndex());
					}
					if (prev2 != null && prev2.getInstruction() instanceof LoadInstruction) {
						comparisonVariables.add(((LoadInstruction) prev2.getInstruction()).getIndex());
					}
				}
			}
		}

		for (InstructionHandle handle = instructionList.getStart(); handle != null; handle = handle.getNext()) {
			Instruction inst = handle.getInstruction();
			InstructionHandle next = handle.getNext();

			// Constant assignment: push followed by store
			if (next != null && inst instanceof ConstantPushInstruction && next.getInstruction() instanceof StoreInstruction) {
				StoreInstruction store = (StoreInstruction) next.getInstruction();
				int varIndex = store.getIndex();
				Number value = null;

				if (inst instanceof BIPUSH) value = ((BIPUSH) inst).getValue();
				else if (inst instanceof SIPUSH) value = ((SIPUSH) inst).getValue();
				else if (inst instanceof ICONST) value = ((ICONST) inst).getValue();
				else if (inst instanceof LCONST) value = ((LCONST) inst).getValue();
				else if (inst instanceof FCONST) value = ((FCONST) inst).getValue();
				else if (inst instanceof DCONST) value = ((DCONST) inst).getValue();
				else if (inst instanceof LDC) {
					Object val = ((LDC) inst).getValue(cpgen);
					if (val instanceof Number) value = (Number) val;
				} else if (inst instanceof LDC2_W) {
					Object val = ((LDC2_W) inst).getValue(cpgen);
					if (val instanceof Number) value = (Number) val;
				}

				if (value != null) {
					currentConstants.put(varIndex, value);
				}

				handle = next;
				continue;
			}

			else if (inst instanceof LoadInstruction) {
				int varIndex = ((LoadInstruction) inst).getIndex();

				if (comparisonVariables.contains(varIndex)) {
					continue;
				}

				if (currentConstants.containsKey(varIndex)) {
					Number value = currentConstants.get(varIndex);
					Instruction newInst = null;

					if (value instanceof Integer) newInst = new LDC(cpgen.addInteger(value.intValue()));
					else if (value instanceof Float) newInst = new LDC(cpgen.addFloat(value.floatValue()));
					else if (value instanceof Long) newInst = new LDC2_W(cpgen.addLong(value.longValue()));
					else if (value instanceof Double) newInst = new LDC2_W(cpgen.addDouble(value.doubleValue()));

					if (newInst != null) {
						InstructionHandle newHandle = instructionList.insert(handle, newInst);
						try {
							instructionList.delete(handle);
							modified = true;

							if (handle.hasTargeters()) {
								for (InstructionTargeter targeter : handle.getTargeters()) {
									targeter.updateTarget(handle, newHandle);
								}
							}

							handle = newHandle;
						} catch (TargetLostException e) {
							for (InstructionHandle lostTarget : e.getTargets()) {
								for (InstructionTargeter targeter : lostTarget.getTargeters()) {
									targeter.updateTarget(lostTarget, newHandle);
								}
							}
						}
					}
				}
			}

			else if (inst instanceof StoreInstruction) {
				int varIndex = ((StoreInstruction) inst).getIndex();
				currentConstants.remove(varIndex);
			}
		}

		if (modified) {
			instructionList.setPositions(true);
		}

		return modified;
	}


	public void write(String optimisedFilePath) {
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