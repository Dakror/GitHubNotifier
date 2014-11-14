package de.dakror.ghnotifier;

import java.util.Arrays;

public class CFG {
	public static void p(Object... p) {
		if (p.length == 1) System.out.println(p[0]);
		else System.out.println(Arrays.toString(p));
	}
	
	public static void e(Object... p) {
		if (p.length == 1) System.err.println(p[0]);
		else System.err.println(Arrays.toString(p));
	}
}
