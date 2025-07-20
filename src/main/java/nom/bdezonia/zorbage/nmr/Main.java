/*
 * zorbage-nmr: : code for populating NMR file data into zorbage structures for further processing
 *
 * Copyright (C) 2023 Barry DeZonia
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package nom.bdezonia.zorbage.nmr;

import nom.bdezonia.zorbage.data.DimensionedDataSource;
import nom.bdezonia.zorbage.misc.DataBundle;
import nom.bdezonia.zorbage.type.real.float32.Float32Member;

/**
 * @author Barry DeZonia
 */
public class Main {
	
	public static void main(String[] args) {
		
		// This one loads well. Should compare my results to what nmrDraw shows.
		
		String filename =
//			"/home/bdezonia/dev/drift-fix/testdata/ayrshire/3D/010_NCOCX_50ms_80x100_part1.fid/NCOCX_part1.ft3";
			"/home/bdz/testdata/images/ft2/2lgi/cc2d/CC50_experiment.ft2";
		
		System.out.println("GOING TO READ FILE: "+filename);
		
		DataBundle files = NmrPipeReader.readAllDatasets(filename);
		
		System.out.println();
		
		System.out.println("number of data files returned = " + files.bundle().size());
		
//		DimensionedDataSource<Float32Member> file = files.flts.get(0);
	}

}
