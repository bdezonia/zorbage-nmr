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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import nom.bdezonia.zorbage.algebra.Algebra;
import nom.bdezonia.zorbage.algebra.Allocatable;
import nom.bdezonia.zorbage.algebra.HasComponents;
import nom.bdezonia.zorbage.algebra.SetFromDouble;
import nom.bdezonia.zorbage.data.DimensionedDataSource;
import nom.bdezonia.zorbage.data.DimensionedStorage;
import nom.bdezonia.zorbage.dataview.TwoDView;
import nom.bdezonia.zorbage.tuple.Tuple5;

/**
 * 
 * @author Barry DeZonia
 *
 */
public class TwoDText {

	/**
	 * Read a two dimensional text file where each row is <row number> <col number> <val1> <val2> ...
	 * into a type of datasource you pass in to this reader.
	 * 
	 * @param <T>
	 * @param <U>
	 * @param filename
	 * @param alg
	 * @param type
	 * @return
	 */
	public static <T extends Algebra<T,U>, U extends Allocatable<U> & SetFromDouble & HasComponents>
		DimensionedDataSource<U> read(String filename, T alg)
	{
		Tuple5<Integer,Long,Long,Long,Long> metadata = metadata(filename);

		int numRealCols = metadata.a();
		
		long minX = metadata.b();
		
		long minY = metadata.c();

		long maxX = metadata.d();
		
		long maxY = metadata.e();
		
		long xDim = maxX - minX + 1;
		
		long yDim = maxY - minY + 1;
		
		U val = alg.construct();
		
		FileReader fr = null;
		
		BufferedReader br = null;
		
		try {
			
			fr = new FileReader(filename);
		
			br = new BufferedReader(fr);
			
			DimensionedDataSource<U> data = DimensionedStorage.allocate(val, new long[] {xDim, yDim});
			
			fr = new FileReader(filename);
			
			br = new BufferedReader(fr);
			
			double[] doubleVals = new double[val.componentCount()];
			
			TwoDView<U> vw = new TwoDView<>(data);
			
			while (br.ready()) {
				
				String line = br.readLine();
				
				String[] terms = line.trim().split("\\s+");
				
				String xStr = terms[0];
				
				String yStr = terms[1];
				
				long x = Long.parseLong(xStr);
				
				long y = Long.parseLong(yStr);
				
				for (int i = 0; i < Math.min(doubleVals.length, numRealCols); i++) {
					
					doubleVals[i] = Double.parseDouble(terms[i + 2]);
				}

				for (int i = Math.min(doubleVals.length, numRealCols); i < val.componentCount(); i++) {

					doubleVals[i] = 0;
				}
				
				val.setFromDouble(doubleVals);
				
				vw.set(x-minX, y-minY, val);
			}
			
			data.setSource(filename);
			
			return data;

		} catch (FileNotFoundException e) {
			
			System.out.println("FILE NOT FOUND : " + filename);
			
		} catch (IOException e) {
			
			System.out.println("IO Exception : " + e.getMessage());
			
		} catch (NumberFormatException e) {
			
			System.out.println("Bad number in data file : " + e.getMessage());
			
		} finally {

			try {
				
				if (br != null) br.close();
			
				if (fr != null) fr.close();
			
			} catch (Exception e) {
				
				;
			}
		}
		
		return null;
	}

	/**
	 * 
	 * @param filename
	 * @return
	 */
	public static Tuple5<Integer,Long,Long,Long,Long> metadata(String filename) {

		FileReader fr = null;
		
		BufferedReader br = null;
		
		try {
			
			fr = new FileReader(filename);
		
			br = new BufferedReader(fr);
			
			// compute extents of data
			
			long minX = Long.MAX_VALUE;
		
			long minY = Long.MAX_VALUE;
			
			long maxX = Long.MIN_VALUE;
			
			long maxY = Long.MIN_VALUE;
			
			int realDataColumns = 0;
			
			while (br.ready()) {
				
				String line = br.readLine();
				
				String[] terms = line.trim().split("\\s+");
				
				realDataColumns = terms.length - 2;
				
				String xStr = terms[0];
				
				String yStr = terms[1];
						
				long x = Long.parseLong(xStr);
				
				long y = Long.parseLong(yStr);
				
				if (x < minX) minX = x;
	
				if (x > maxX) maxX = x;
				
				if (y < minY) minY = y;
				
				if (y > maxY) maxY = y;
			}
			
			br.close();
			
			fr.close();
	
			return new Tuple5<Integer,Long,Long,Long,Long>(realDataColumns, minX, maxX, minY, maxY);
			
		} catch (Exception e) {
			
			return null;
			
		} finally {

			try {
					
				if (br != null) br.close();
				
				if (fr != null) fr.close();
				
			} catch (Exception e) {
					
				;
			}
		}
	}
}
