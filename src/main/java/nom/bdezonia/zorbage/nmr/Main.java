package nom.bdezonia.zorbage.nmr;

import nom.bdezonia.zorbage.misc.DataBundle;

public class Main {
	
	public static void main(String[] args) {
		
		String filename = "/home/bdezonia/dev/drift-fix/testdata/ayrshire/3D/010_NCOCX_50ms_80x100_part1.fid/NCOCX_part1.ft3";
		
		System.out.println("GOING TO READ FILE: "+filename);
		
		DataBundle files = NmrPipeReader.open(filename);
	}

}
