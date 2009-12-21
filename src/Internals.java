

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Internals {
	private static final int PREFIX = 0;
	private static final int INFIX = 1;
	private static final int POSTFIX = 2;
	private static final int VARIABLES = 3;
	private static final int INTERNALS = 4;
	private static final int PREDEFWORDS = 5;

	private static String escape(String s) {
		return s.replaceAll("\\\\", "\\\\\\\\").replaceAll("\\\"", "\\\\\"");
	}
	
	public static short findWord(List internals, List predefs, String name) throws IOException {
		int i = internals.lastIndexOf(name);
		if (i >= 0) return (short)i;
		i = predefs.lastIndexOf(name);
		if (i >= 0) return (short)(i + internals.size());
		throw new IOException();
	}
	
	public static void litstring_compile(String s, ArrayList l) {
		byte[] bytes = s.getBytes();
		int len = bytes.length;
		if (len > 255)
			throw new IllegalArgumentException("String too long (" + len + " bytes)");
		int temp = len;
		for (int i = 0, shortl = (len & -2) + 1; i < shortl; i++) {
			if ((i & 1) == 0)
				l.add(new Short((short)(temp << 8 | (i == len ? 0 : (bytes[i] & 0xff)))));
			else
				temp = bytes[i];
		}
	}
		
	public static void main(String[] args) {
		Internals i = new Internals();
		i.compile(args);
	}
	
	public void compile(String[] args) {
		System.out.println("compiling stos...\nAdding Prefix data");
		String outpath = "Stos.java";
		ArrayList wordNames = new ArrayList();
		ArrayList wordConsts = new ArrayList();
		ArrayList wordComments = new ArrayList();
		ArrayList codeList = new ArrayList();
		ArrayList predefNames = new ArrayList();
		ArrayList predefCode = new ArrayList();
		ArrayList predefComments = new ArrayList();
		ArrayList variables = new ArrayList();
		StringBuffer templatePrefix = new StringBuffer();
		StringBuffer templateInfix = new StringBuffer();
		StringBuffer templatePostfix = new StringBuffer();
		StringBuffer predefbuf = new StringBuffer();
		StringBuffer predefwordComment = new StringBuffer();
		int maxIntWordLen = 0;
		HashMap flags = new HashMap();
		flags.put("IMMEDIATE", new Integer(1));
		flags.put("HIDDEN", new Integer(2));
		flags.put("COMPILE-ONLY", new Integer(4));
		String wordName = null;
		String wordConst = null;
		String wordComment = null;
		BufferedReader br = null;
		int state = PREFIX;
		int internalSize = 0;
		Short litstring = null;
		Short idxVariables = null;
		Short idxShort = null;
		Short idxFalse = null;
		Short idxOne = null;
		Short idxInt = null;
		try {
			InputStream input = getClass().getResourceAsStream("stos-internals.txt");
			br = new BufferedReader(new InputStreamReader(input));
			String line = br.readLine();
			StringBuffer body = new StringBuffer();
			while (line != null) {
				if ("========".equals(line)) {
					if (state == INTERNALS) {
						wordNames.add(wordName);
						maxIntWordLen = Math.max(maxIntWordLen, wordConst.length());
						wordConsts.add(wordConst);
						wordComments.add(wordComment);
						codeList.add(body.toString());
						internalSize = wordConsts.size();
						litstring = new Short((short)wordConsts.indexOf("litstring_compile"));
						idxVariables = new Short((short)wordNames.indexOf("vars"));
						idxFalse = new Short((short)wordNames.indexOf("false"));
						idxOne = new Short((short)wordNames.indexOf("'1"));
						idxInt = new Short((short)wordNames.indexOf("int"));
					}
					state++;
					switch(state) {
					case PREFIX: System.out.println("Compiling Prefix data"); break;
					case INFIX: System.out.println("Compiling Infix data"); break;
					case POSTFIX: System.out.println("Compiling Postfix data"); break;
					case VARIABLES: System.out.println("Compiling Variables"); break;
					case INTERNALS: System.out.println("Compiling Internals"); break;
					case PREDEFWORDS: System.out.println("Compiling predefwords"); break;
					default: System.out.println("Done compiling.");
					}
					line = br.readLine();
					continue;
				}
				switch (state) {
				case PREFIX:
					if (line.indexOf("/*CONSTS*/") >= 0) {
						System.out.println("Adding PreInfix data");
						state = INFIX;
					} else {
						templatePrefix.append(line);
						templatePrefix.append('\n');
					}
					break;
				case INFIX:
					if (line.indexOf("/*CODE*/") >= 0) {
						state = POSTFIX;
						System.out.println("Adding PostFix data");
					} else {
						templateInfix.append(line);
						templateInfix.append('\n');
					}
					break;
				case POSTFIX:
					templatePostfix.append(line);
					templatePostfix.append('\n');
					break;
				case VARIABLES:
					variables.add(line);
					break;
				case INTERNALS:
					if ("".equals(line)) { // ignore empty lines
						line = br.readLine();
						continue;
					}
					char first = line.charAt(0);
					if (first != '\t' && first != ' ') { // new word definition
						if (body.length() > 0) { // old word definition, add to code
							wordNames.add(wordName);
							maxIntWordLen = Math.max(maxIntWordLen, wordConst.length());
							wordConsts.add(wordConst);
							wordComments.add(wordComment);
							codeList.add(body.toString());
							body.setLength(0);
						}
						String[] temp = line.split("[\\s\\t]", 3);
						wordName = temp[0].replaceAll("[\\s\\t]", "");
						if (temp.length == 1 || temp[1].matches("^[\\s\\ŧ]*$")) {
							wordConst = wordName;
						} else {
							wordConst = temp[1].replaceAll("[\\s\\t]+", ""); 
						}
						if (temp.length == 3) {
							wordComment = temp[2];
						} else {
							wordComment = null;
						}
					} else {
						body.append("\t");
						body.append(line);
						body.append('\n');
					}
					break;
				case PREDEFWORDS:
					predefbuf.append(line);
					if (!line.matches(".*;\\s*$")) {
						predefbuf.append('\n');
						break; // allow for multiline words
					}
					String wordText = predefbuf.toString().trim();
					String[] elems = wordText.split("\\s+", 3);
					wordName = elems[1];
					if ("'".equals(wordName))
						idxShort = new Short((short)wordConsts.size());
					predefbuf.setLength(0);
					if (!":".equals(elems[0]))
						System.out.println("Warning: Line does not start with ':' - " + line);
					wordText = elems[2];
					String predefConst = null;
					int flag = 0;
					ArrayList wlist = new ArrayList();
					HashMap labels = new HashMap();
					int commentCount = 0;
					int f = 0;
					boolean firstComment = true;
					predefwordComment.setLength(0);
					while (true) {
						elems = wordText.split("\\s+", 2);
						String elem = elems[0];
						if (elems.length < 2) break;
						wordText = elems[1];
						if ("".equals(elem) || elem == null)
							continue;
						if ("(".equals(elem)) {
							commentCount++;
						} else if (")".equals(elem)) {
							commentCount--;
							firstComment = false;
							continue;
						}
						if (commentCount > 0) {
							if (elem.matches("'\\S+")) {
								predefConst = elem.substring(1);
							} else if (firstComment) {
								predefwordComment.append(elem);
								predefwordComment.append(' ');
							}
							continue;
						}
						if (flags.containsKey(elem)) {
							f++;
							flag |= ((Integer)flags.get(elem)).intValue();
							continue;
						}
						if ("recurse".equals(elem)) {
							wlist.add(new Short((short)wordConsts.size()));
							continue;
						} 
						if (elem.startsWith("%")) {
							int idx = wordConsts.indexOf(elem.substring(1));
							if (idx == -1) {
								if (elem.substring(1).equals(predefConst)) {
									idx = wordConsts.size();
								} else {
									System.out.println("Could not find " + elem.substring(1) + 
											" in definition of " + wordName);
									System.out.println(predefConst);
								}								
							}
							wlist.add(new Short((short)idx));
							continue;
						}
						int varIndex = -1;
						if (elem.startsWith("$")) { // Builtin Variable
							varIndex = variables.indexOf(elem.substring(1));
						}
						if (varIndex >= 0) {
							wlist.add(idxVariables);
							if (varIndex == 0) {
								wlist.add(idxFalse);
							} else if (varIndex == 1) {
								wlist.add(idxOne);
							} else {
								wlist.add(idxShort);
								wlist.add(new Short((short)varIndex));
							}
						} else if (elem.matches("^:[A-Z0-9]+$")) { // Label
							labels.put(elem.substring(1), new Integer(wlist.size()));
							continue;
						} else if (elem.matches("^[A-Z0-9]+:$")) { // Ref
							wlist.add(elem.replaceAll(":$", ""));
							continue;
						} else if (".\"".equals(elem)) {
							wlist.add(litstring);
							elems = wordText.split("\"\\s*", 2);
							litstring_compile(elems[0], wlist);
							wordText = elems[1];
						} else {
							try {
								int wi = findWord(wordNames, predefNames, elem);
								if (wi < 0)
									System.out.println(elem + "?" + " (" + wordName + ")");
								wlist.add(new Short((short)wi));
							} catch (IOException e) {
								int value = 0;
								try {
									 value = Integer.parseInt(elem);
								} catch (NumberFormatException nfe) { 
									System.out.println("ERROR: Could not find " + elem + 
											" in the definition of " + wordName);
								}
								Object last = null;
								if (!wlist.isEmpty())
									last = wlist.get(wlist.size() - 1);
								if (idxInt.equals(last)) {
									wlist.add(new Short((short)(value >>> 16)));
									wlist.add(new Short((short)(value & 0xFFFF)));
								} else {
									wlist.add(new Short((short)value));
								}
							}
						}
					}
					predefNames.add(escape(wordName));
					predefComments.add(predefwordComment.toString());
					wordConsts.add(predefConst);
					short[] word = new short[wlist.size() + 1];
					word[0] = (short)flag;
					for (int i = 1; i < word.length; i++) {
						Object w = wlist.get(i - 1);
						if (w instanceof String) { // ref
							word[i] = (short)(((Integer)labels.get(w)).intValue() - i);
						} else { // word
							word[i] = ((Short)w).shortValue();
						}
					}
					predefCode.add(word);
					break;
				default:
					System.out.println("This should not happen.");
					throw new IOException();
				}
				line = br.readLine();
			}
		} catch (IOException e) {
			e.printStackTrace();
			return;
		} finally {
			if (br != null)
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new FileWriter(outpath));
			// Prefix
			bw.write(templatePrefix.toString());
			// Consts
			bw.write("\tprivate static final int INTERNAL_SIZE = ");
			bw.write(String.valueOf(internalSize));
			bw.write(";\n\n");
			for (int i = 0; i < internalSize; i++) {
				bw.write("\tprivate static final short WORD_");
				bw.write(((String)wordConsts.get(i)).toUpperCase());
				bw.write(" = (short)");
				bw.write(String.valueOf(i));
				bw.write(";\n");
			}
			for (int i = internalSize, size = wordConsts.size(); i < size; i++) {
				String wconsts = ((String)wordConsts.get(i));
				if (wconsts == null)
					continue;
				String wconst = "WORD_" + wconsts.toUpperCase();
				if (templatePrefix.indexOf(wconst) < 0 && templateInfix.indexOf(wconst) < 0
						&& templatePostfix.indexOf(wconst) < 0) {
					boolean contained = false;
					for (int c = 0; c < codeList.size(); c++) {
						if (((String)codeList.get(c)).indexOf(wconst) >= 0) {
							contained = true;
							break;
						}
					}
					if (!contained)
						continue; // WORD_FOO not in code, so no need to define it.
				}
				bw.write("\tprivate static final short ");
				bw.write(wconst);
				bw.write(" = (short)");
				bw.write(String.valueOf(i));
				bw.write(";\n");
			}
			// Names
			bw.write("\n\tprivate static final String[] INTERNAL_NAMES = {\n");
			for (int i = 0; i < internalSize; i++) {
				bw.write("\t\t\"");
				bw.write(escape((String)wordNames.get(i)));
				bw.write("\" /*" + i + "*/,\n");
			}
			int plen = predefNames.size();
			for (int i = 0; i < plen; i++) {
				bw.write("\t\t\"");
				bw.write((String)predefNames.get(i));
				bw.write("\" /*" + (internalSize + i) + "*/,\n");
			}
			bw.write("\t};\n\n\tprivate static final short[][] INTERNAL_WORDS = {\n");
			for (int i = 0; i < plen; i++) {
				bw.write("\t\t{/*");
				bw.write(String.valueOf(internalSize + i));
				bw.write(' ');
				bw.write((String)predefNames.get(i));
				String comment = (String)predefComments.get(i);
				if (!"".equals(comment)) {
					bw.write(' ');
					bw.write(comment);
					bw.write(')');
				}
				bw.write("*/");
				short[] word = (short[])predefCode.get(i);
				int clen = (wordName.length() + comment.length()) >>> 2;
				for (int j = 0; j < word.length; j++) {
					if (j > 0) {
						bw.write(", ");
						if ((j + clen) % 16 == 0) bw.write("\n\t\t\t");
					}
					bw.write(String.valueOf(word[j]));
				}
				bw.write("},\n");
			}
			bw.write("\t};\n\n");
			// Variables
			int vsize = variables.size();
			for (int i = 0; i < vsize; i++) {
				bw.write("\tprivate static final int ");
				bw.write((String)variables.get(i));
				bw.write(" = ");
				bw.write(String.valueOf(i));
				bw.write(";\n");
			}
			bw.write("\tprivate static final int INITIAL_VAR_TOP = ");
			bw.write(String.valueOf(vsize));
			bw.write(";\n\n");
			bw.write(templateInfix.toString());
			// functions
			for (int i = 0; i < internalSize; i++) {
				if (wordComments.get(i) != null) {
					bw.write("\t/**\n\t * ");
					bw.write(((String)wordComments.get(i)).replaceAll("\\\\n", "\n\t * "));
					bw.write("\n\t */\n");
				}
				bw.write("\tprivate void ");
				bw.write((String)wordConsts.get(i));
				bw.write("() {\n");
				bw.write((String)codeList.get(i));
				bw.write("\t}\n\n");
			}
			// Code
			bw.write("\t/**\n\t * dispatches a word to the appropriate handler.\n\t * @param w  the number of the word.\n\t */\n\tprivate void dostep(short w) {\n\t\tswitch (w) {\n");
			for (int i = 0; i < internalSize; i++) {
				String wconst = (String)wordConsts.get(i);
				bw.write("\t\t\tcase WORD_");
				bw.write(wconst.toUpperCase());
				bw.write(":");
				int l = wconst.length();
				int tabs = (maxIntWordLen - l + 7) >> 2;
				for (int t = 0; t < tabs; t++) {
					bw.write("\t");
				}
				bw.write(wconst);
				bw.write("();");
				for (int t = 0; t < tabs; t++) {
					bw.write("\t");
				}
				bw.write("break;\n");
			}
			bw.write("\t\t\tdefault:");
			int tabs = (maxIntWordLen + 7) >> 2;
			for (int t = 0; t < tabs; t++)
				bw.write("\t");
			bw.write("docol(w);\n\t\t}\n\t}\n");
			bw.write(templatePostfix.toString());
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (bw != null)
				try {
					bw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		System.out.println("done.");
	}
}
