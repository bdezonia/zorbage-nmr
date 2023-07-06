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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import nom.bdezonia.zorbage.algebra.Algebra;
import nom.bdezonia.zorbage.algebra.GetAsDoubleArray;
import nom.bdezonia.zorbage.data.DimensionedDataSource;
import nom.bdezonia.zorbage.sampling.IntegerIndex;

/**
 * 
 * @author Barry DeZonia
 *
 */
public class PipeToTextWriter {

	/**
	 * Write a 1d or 2d data set into NMRPipe's PipeToText format
	 * @param filename Name of output file.
	 * @param alg The algebra that can access values from the 1d or 2d data set. 
	 * @param data The 1-d or 2-d data set to save to disk.
	 */
	public static <T extends Algebra<T,U>, U extends GetAsDoubleArray>
	
		void write(String filename, T alg, DimensionedDataSource<U> data)
	{
		int numD = data.numDimensions();
		
		if (numD != 1 && numD != 2)
			throw new IllegalArgumentException("PipeToTextWriter only writes files from 1d or 2d data sources.");
		
		U value = alg.construct();
		
		long cols = data.dimension(0);
		
		long rows = (numD == 1) ? 1 : data.dimension(1);

		IntegerIndex idx = new IntegerIndex(numD);
		
		FileWriter fw = null;
		
		BufferedWriter bw = null;

		try {

			fw = new FileWriter(filename);
			
			bw = new BufferedWriter(fw);
			
			for (long r = rows - 1; r >= 0; r--) {  // PIPE has flipped Y coords
				
				if (numD > 1)
					idx.set(1, r);

				for (long c = 0; c < cols; c++) {
					
					idx.set(0, c);
					
					data.get(idx, value);
					
					StringBuilder b = new StringBuilder();
					
					b.append(c);
					b.append(' ');
					
					b.append(rows - 1 - r);
					b.append(' ');
					
					final double[] values = value.getAsDoubleArray();
					
					for (int i = 0; i < values.length; i++) {
					
						b.append(values[i]);
						b.append(' ');
					}
					
					b.append('\n');

					bw.write(b.toString());
				}
			}
			
			bw.close();
			
			fw.close();
			
		} catch (IOException e) {
			
			System.out.println("IO Exception: " + e.getMessage());
		}
	}
}
