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
import nom.bdezonia.zorbage.algebra.G;
import nom.bdezonia.zorbage.algebra.HasComponents;
import nom.bdezonia.zorbage.algebra.SetFromDoubles;
import nom.bdezonia.zorbage.data.DimensionedDataSource;
import nom.bdezonia.zorbage.data.DimensionedStorage;
import nom.bdezonia.zorbage.dataview.TwoDView;
import nom.bdezonia.zorbage.misc.DataBundle;
import nom.bdezonia.zorbage.tuple.Tuple5;
import nom.bdezonia.zorbage.type.complex.float64.ComplexFloat64Member;
import nom.bdezonia.zorbage.type.octonion.float64.OctonionFloat64Member;
import nom.bdezonia.zorbage.type.quaternion.float64.QuaternionFloat64Member;
import nom.bdezonia.zorbage.type.real.float64.Float64Member;

// TODO: this code is quite wrong. pipe2txt.tcl can handle and write n-d data

/**
 * 
 * @author Barry DeZonia
 *
 */
public class PipeToTextReader {

	/**
	 * Get important metadata about the given NMRPipe text data file.
	 * Used by the file readers to know how to allocate and populate
	 * a correct data grid.
	 * 
	 * @param filename Name of the NMRPipe text data file that contains numeric values.
	 *  
	 * @return A tuple of (numComponents,minCol,maxCol,minRow,maxRow).
	 */
	public static
	
		Tuple5<Integer,Long,Long,Long,Long>
	
			readMetadata(String filename)
	{
		FileReader fr = null;
		
		BufferedReader br = null;
		
		try {
			
			fr = new FileReader(filename);
		
			br = new BufferedReader(fr);
			
			// compute extents of data
			
			long minC = Long.MAX_VALUE;
		
			long minR = Long.MAX_VALUE;
			
			long maxC = Long.MIN_VALUE;
			
			long maxR = Long.MIN_VALUE;
			
			int numComponents = 0;
			
			while (br.ready()) {
				
				String line = br.readLine();
				
				String[] terms = line.trim().split("\\s+");
				
				numComponents = terms.length - 2;
				
				String cStr = terms[0];
				
				String rStr = terms[1];
						
				long c = Long.parseLong(cStr);
				
				long r = Long.parseLong(rStr);
				
				if (c < minC) minC = c;
	
				if (c > maxC) maxC = c;
				
				if (r < minR) minR = r;
				
				if (r > maxR) maxR = r;
			}
			
			br.close();
			
			fr.close();
	
			return new Tuple5<Integer,Long,Long,Long,Long>(numComponents, minC, maxC, minR, maxR);
			
		} catch (Exception e) {

			System.out.println("Exception detected: "+e.getMessage());
			
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

	/**
	 * Read a two dimensional NMRPipe exported text file where each row is
	 *  <row number> <col number> <val1> <val2> ...
	 * and return a type of data source based upon the algebra you pass in
	 * to this reader. This file layout is based on some extraction or
	 * conversion of NmrPipe data. This reader can make a gridded
	 * data set of various types (for example reals, complexes, quaternions,
	 * octonions, data tables, etc.) based upon the Algebra passed to this
	 * reader. Note that the convention seems to be that the origin of the
	 * data is at the upper left corner. Zorbage has a lower left origin
	 * convention and thus the data is flipped in y during reading.  
	 * 
	 * @param <T> the algebra
	 * @param <U> the types manipulated by the algebra
	 * @param filename Name of the acsii text data file that contains numeric values.
	 * @param alg The algebra used to create the kind of data values we want.
	 * @param type The kind of data values we are wanting to create.
	 * @return The file data as read into a DimensionedDataSource.
	 */
	public static <T extends Algebra<T,U>,
					U extends Allocatable<U> & SetFromDoubles & HasComponents>
	
		DimensionedDataSource<U>
	
			read(String filename, T alg)
	{
		Tuple5<Integer,Long,Long,Long,Long> metadata = readMetadata(filename);

		int numComponents = metadata.a();
		
		long minC = metadata.b();
		
		long maxC = metadata.c();
		
		long minR = metadata.d();
		
		long maxR = metadata.e();
		
		long rows = maxR - minR + 1;

		//System.out.println((maxR-minR+1) + " rows " + (maxC-minC+1) + " cols");
		
		U val = alg.construct();
		
		FileReader fr = null;
		
		BufferedReader br = null;
		
		try {
			
			DimensionedDataSource<U> data = DimensionedStorage.allocate(val, new long[] {maxC-minC+1, maxR-minR+1});
			
			fr = new FileReader(filename);
			
			br = new BufferedReader(fr);
			
			double[] doubleVals = new double[val.componentCount()];
			
			TwoDView<U> vw = new TwoDView<>(data);
			
			while (br.ready()) {
				
				String line = br.readLine();
				
				String[] terms = line.trim().split("\\s+");
				
				String cStr = terms[0];
				
				String rStr = terms[1];
				
				long c = Long.parseLong(cStr) - minC;
				
				long r = Long.parseLong(rStr) - minR;
				
				for (int i = 0; i < Math.min(val.componentCount(), numComponents); i++) {
					
					doubleVals[i] = Double.parseDouble(terms[2 + i]);
				}

				for (int i = Math.min(val.componentCount(), numComponents); i < val.componentCount(); i++) {

					doubleVals[i] = 0;
				}
				
				val.setFromDoubles(doubleVals);
				
				// old way that flipped y
				vw.set(c, rows-1-r, val);
				
				// new way that matches FRC code's TwoDTextReader so that
				//   Barry can test his GFRC metric method on nmr pipe data.
				
				//vw.set(c, r, val);
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
	 * Open a NMRPipe text file as real double data.
	 *   
	 * @param filename
	 * @return
	 */
	public static
	
		DimensionedDataSource<Float64Member>
	
			readDouble(String filename)
	{
		
		return PipeToTextReader.read(filename, G.DBL);
	}

	/**
	 * Open a NMRPipe text file as complex double data.
	 * 
	 * @param filename
	 * @return
	 */
	public static
	
		DimensionedDataSource<ComplexFloat64Member>
	
			readComplexDouble(String filename)
	{
		return PipeToTextReader.read(filename, G.CDBL);
	}

	/**
	 * Open a NMRPipe text file as quaternion double data.
	 * 
	 * @param filename
	 * @return
	 */
	public static
	
		DimensionedDataSource<QuaternionFloat64Member>
	
			readQuaternionDouble(String filename)
	{
		return PipeToTextReader.read(filename, G.QDBL);
	}
	
	/**
	 * Open a NMRPipe text file as octonion double data.
	 * 
	 * @param filename
	 * @return
	 */
	public static
	
		DimensionedDataSource<OctonionFloat64Member>
	
			readOctonionDouble(String filename)
	{
		return PipeToTextReader.read(filename, G.ODBL);
	}
	
	/**
	 * Open an NMRPipe text file and return it in a DataBundle.
	 * Depending upon the number of data columns in the file
	 * this routine can create real, complex, quaternion, or
	 * octonion data sets.
	 * 
	 * @param filename
	 * @return
	 */
	public static
	
		DataBundle
		
			read(String filename)
	{
		DataBundle bundle = new DataBundle();
		
		Tuple5<Integer,Long,Long,Long,Long> fileMetaData =
				PipeToTextReader.readMetadata(filename);
		
		int numComponents = fileMetaData.a();

		if (numComponents < 1 || numComponents > 8) {

			throw
				new IllegalArgumentException(
						"text file must have data column count between 1 and 8");
		}
		else if (numComponents <= 1) {
			
			bundle.dbls.add( readDouble(filename) );
		}
		else if (numComponents <= 2) {
			
			bundle.cdbls.add( readComplexDouble(filename) );
		}
		else if (numComponents <= 4) {
			
			bundle.qdbls.add( readQuaternionDouble(filename) );
		}
		else {  // if here it must be between 5 and 8 components
			
			bundle.odbls.add( readOctonionDouble(filename) );
		}
		
		return bundle;
	}
}
