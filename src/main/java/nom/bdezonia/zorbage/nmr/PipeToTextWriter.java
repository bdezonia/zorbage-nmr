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
import nom.bdezonia.zorbage.misc.DataSourceUtils;
import nom.bdezonia.zorbage.sampling.IntegerIndex;
import nom.bdezonia.zorbage.sampling.SamplingCartesianIntegerGrid;
import nom.bdezonia.zorbage.sampling.SamplingIterator;

/**
 * 
 * @author Barry DeZonia
 *
 */
public class PipeToTextWriter {

	/**
	 * Write a data set into NMRPipe's PipeToText format. Note that it
	 * will only write real or complex valued data. Other data types
	 * will have their extra components ignored.
	 * @param filename Name of output file.
	 * @param alg The algebra that can access values from the data set. 
	 * @param data The data set to save to disk.
	 */
	public static <T extends Algebra<T,U>, U extends GetAsDoubleArray>
	
		void write(String filename, T alg, DimensionedDataSource<U> data)
	{
		FileWriter fw = null;
		
		BufferedWriter bw = null;

		try {

			fw = new FileWriter(filename);
			
			bw = new BufferedWriter(fw);
			
			long[] dims = DataSourceUtils.dimensions(data);
			
			int numD = dims.length;
			
			IntegerIndex minPt = new IntegerIndex(numD);
			
			IntegerIndex maxPt = new IntegerIndex(numD);
			
			for (int i = 0; i < numD; i++) {
				
				maxPt.set(i, dims[i]-1);
			}
			
			SamplingCartesianIntegerGrid sampling = new SamplingCartesianIntegerGrid(minPt, maxPt);
			
			SamplingIterator<IntegerIndex> iter = sampling.iterator();
			
			U value = alg.construct();
			
			IntegerIndex idx = new IntegerIndex(numD);
			
			while (iter.hasNext()) {
				
				iter.next(idx);

				data.get(idx, value);
				
				double[] values = value.getAsDoubleArray();
				
				StringBuilder b = new StringBuilder();
				
				// TODO: PIPE has flipped Y coords and maybe more: must account for
				
				// TODO: Am I reporting the x,y,z,... grid in correct order? or do I need to reverse them? 
				
				for (int i = 0; i < numD; i++) {
					
					b.append(' ');
					b.append(idx.get(i) + 1);  // plus one because that is how the NMRPipe's pipe2text.tcl behaves 
				}
				
				// pipe's data format is limited to reals and complexes so write no more than 2 values
				
				for (int i = 0; i < Math.min(2, values.length); i++) {
				
					b.append(' ');
					b.append(values[i]);
				}
				
				b.append('\n');

				bw.write(b.toString());
			}
			
			bw.close();
			
			fw.close();
			
		} catch (IOException e) {
			
			System.out.println("IO Exception: " + e.getMessage());
		}
	}
}
