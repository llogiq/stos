package helper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
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
		int i = internals.indexOf(name);
		if (i >= 0) return (short)i;
		i = predefs.indexOf(name);
		if (i >= 0) return (short)(i + internals.size());
		if (!name.matches("\\d+"))
			System.out.println("Could not find " + name);
		throw new IOException();
	}
	
	public static void main(String[] args) {
		System.out.println("compiling stos...\nAdding Prefix data");
		String inpath = "C:\\Dokumente und Einstellungen\\andre\\workspace\\stos\\src\\stos-internals.txt";
		String outpath = "C:\\Dokumente und Einstellungen\\andre\\workspace\\stos\\src\\Stos.java";
		ArrayList wordNames = new ArrayList();
		ArrayList wordConsts = new ArrayList();
		ArrayList codeList = new ArrayList();
		ArrayList predefNames = new ArrayList();
		ArrayList predefCode = new ArrayList();
		ArrayList variables = new ArrayList();
		StringBuffer templatePrefix = new StringBuffer();
		StringBuffer templateInfix = new StringBuffer();
		StringBuffer templatePostfix = new StringBuffer(); 
		String wordName = null;
		String wordConst = null;
		BufferedReader br = null;
		int state = PREFIX;
		try {
			br = new BufferedReader(new FileReader(inpath));
			String line = br.readLine();
			StringBuffer body = new StringBuffer();
			while (line != null) {
				if ("========".equals(line)) {
					if (state == INTERNALS) {
						wordNames.add(wordName);
						wordConsts.add(wordConst);
						codeList.add(body.toString());
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
							wordConsts.add(wordConst);
							codeList.add(body.toString());
							body.setLength(0);
						}
						String[] temp = line.split("[\\s\\t]+");
						wordName = temp[0].replaceAll("[\\s\\t]", "");
						if (temp.length == 1) {
							wordConst = wordName;
						} else {
							wordConst = temp[1].replaceAll("[\\s\\t]", ""); 
						}
					} else {
						body.append("\t");
						body.append(line);
						body.append('\n');
					}
					break;
				case PREDEFWORDS:
					// A line is a word
					String[] elems = line.split(" ");
					if (!":".equals(elems[0]))
						System.out.println("Warning: Line does not start with ':' - " + line);
					String name = elems[1];
					int l = elems.length - 1;
					short flag = 0;
					if ("IMMEDIATE".equals(elems[l])) {
						l--;
						flag = 1;
					}
					ArrayList wlist = new ArrayList();
					for (int i = 2; i < l; i++) {
						try {
							wlist.add(new Short(findWord(wordNames, predefNames, elems[i])));
						} catch (IOException e) {
							int value = 0;
							try {
								 value = Integer.parseInt(elems[i]);
							} catch (NumberFormatException nfe) { nfe.printStackTrace(); }
							if (((Short)wlist.get(wlist.size() - 1)).shortValue() == 
									wordNames.indexOf("int")) {
								wlist.add(new Short((short)(value >>> 16)));
								wlist.add(new Short((short)(value & 0xFFFF)));
							} else {
								wlist.add(new Short((short)value));
							}
						}
					}
					short[] word = new short[wlist.size() + 1];
					word[0] = flag;
					for (int i = 1; i < word.length; i++) {
						word[i] = ((Short)wlist.get(i - 1)).shortValue();
					}
					predefNames.add(name);
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
			int size = wordConsts.size();
			bw = new BufferedWriter(new FileWriter(outpath));
			// Prefix
			bw.write(templatePrefix.toString());
			// Consts
			bw.write("\tprivate static final int INTERNAL_SIZE = " + size + ";\n\n");
			for (int i = 0; i < size; i++) {
				bw.write("\tprivate static final short WORD_" + 
						((String)wordConsts.get(i)).toUpperCase() + " = (short)" + i + ";\n");
			}
			// Names
			bw.write("\tprivate static final String[] internalNames = {\n");
			for (int i = 0; i < size; i++) {
				bw.write("\t\t\"");
				bw.write(escape((String)wordNames.get(i)));
				bw.write("\" /*" + i + "*/,\n");
			}
			int plen = predefNames.size();
			for (int i = 0; i < plen; i++) {
				bw.write("\t\t\"");
				bw.write((String)predefNames.get(i));
				bw.write("\" /*" + (size + i) + "*/,\n");
			}
			bw.write("\t};\n\n\tprivate static final short[][] INTERNAL_WORDS = {\n");
			for (int i = 0; i < plen; i++) {
				bw.write("\t\t{/*");
				bw.write((String)predefNames.get(i));
				bw.write("*/");
				short[] word = (short[])predefCode.get(i);
				for (int j = 0; j < word.length; j++) {
					if (j > 0) bw.write(", ");
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
			for (int i = 0; i < size; i++) {
				bw.write("\tprivate void ");
				bw.write((String)wordConsts.get(i));
				bw.write("() {\n");
				bw.write((String)codeList.get(i));
				bw.write("\t}\n\n");
			}
			// Code
			bw.write("\tprivate void doword() {\n\t\tswitch (pc >>> 16) {\n");
			for (int i = 0; i < size; i++) {
				bw.write("\t\t\tcase WORD_");
				bw.write(((String)wordConsts.get(i)).toUpperCase());
				bw.write(":\t");
				bw.write((String)wordConsts.get(i));
				bw.write("(); break;\n");
			}
			bw.write("\t\t\tdefault:\n\t\t\t\tdocol();\n\t\t\t\treturn;\n\t\t}\n\t\tnext();\n\t}\n");
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
