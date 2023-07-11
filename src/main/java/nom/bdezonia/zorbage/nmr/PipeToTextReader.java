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
import java.util.HashSet;
import java.util.Set;

import nom.bdezonia.zorbage.algebra.Algebra;
import nom.bdezonia.zorbage.algebra.Allocatable;
import nom.bdezonia.zorbage.algebra.G;
import nom.bdezonia.zorbage.algebra.HasComponents;
import nom.bdezonia.zorbage.algebra.SetFromDoubles;
import nom.bdezonia.zorbage.data.DimensionedDataSource;
import nom.bdezonia.zorbage.data.DimensionedStorage;
import nom.bdezonia.zorbage.misc.DataBundle;
import nom.bdezonia.zorbage.sampling.IntegerIndex;
import nom.bdezonia.zorbage.sampling.RealIndex;
import nom.bdezonia.zorbage.tuple.Tuple4;
import nom.bdezonia.zorbage.type.complex.float64.ComplexFloat64Member;
import nom.bdezonia.zorbage.type.octonion.float64.OctonionFloat64Member;
import nom.bdezonia.zorbage.type.quaternion.float64.QuaternionFloat64Member;
import nom.bdezonia.zorbage.type.real.float64.Float64Member;

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
	 * @return A tuple of (numDims, numDecimalCols, minDim, maxDim).
	 */
	public static
	
		Tuple4<Integer,Integer,IntegerIndex,IntegerIndex>
	
			readMetadata(String filename)
	{
		FileReader fr = null;
		
		BufferedReader br = null;

		try {
			
			fr = new FileReader(filename);
		
			br = new BufferedReader(fr);
			
			RealIndex min = null;

			RealIndex max = null;
			
			int numCols = 0;
			
			int numDims = 0;

			int numDecimalCols = 0;

			Set<Integer> decimalCols = new HashSet<>(); 
			
			while (br.ready()) {
				
				String line = br.readLine();
				
				String[] terms = line.trim().split("\\s+");
				
				numCols = terms.length;

				if (min == null) {
					
					min = new RealIndex(numCols);
					max = new RealIndex(numCols);
					
					for (int i = 0; i < numCols; i++) {
						
						min.set(i, Double.MAX_VALUE);
						max.set(i, -Double.MAX_VALUE);
					}
				}
				
				for (int i = 0; i < numCols; i++) {
					
					double val = Double.parseDouble(terms[i]);
					
					if (Math.floor(val) != val)
						decimalCols.add(i);
					
					if (val < min.get(i))
						min.set(i, val);
					
					if (val > max.get(i))
						max.set(i, val);
				}
			}

			numDecimalCols = decimalCols.size();
			
			numDims = numCols - numDecimalCols;

			IntegerIndex minDim = new IntegerIndex(numDims);
			
			IntegerIndex maxDim = new IntegerIndex(numDims);
			
			for (int i = 0; i < numDims; i++) {
				
				long minVal = (long) min.get(i); 
				
				long maxVal = (long) max.get(i);
				
				minDim.set(i, minVal);
				
				maxDim.set(i, maxVal);
			}
	
			return new Tuple4<Integer,Integer,IntegerIndex,IntegerIndex>(numDims, numDecimalCols, minDim, maxDim);
			
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
	 * Read a NMRPipe exported text file where each row is
	 *  <dim number 1> <dim number 2> ... <data val 1> <optional data val 2>
	 * and return a type of data source based upon the algebra you pass in
	 * to this reader. The file layout is based on some extraction or
	 * conversion of NmrPipe data. This reader can make a gridded
	 * data set of various types (for example reals, complexes, quaternions,
	 * octonions, data tables, etc.) based upon the Algebra passed to this
	 * reader.
	 * 
	 * @param <T> The algebra.
	 * @param <U> The types manipulated by the algebra.
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
		Tuple4<Integer,Integer,IntegerIndex,IntegerIndex> metadata = readMetadata(filename);

		int numDims = metadata.a();
		
		int numDecimalCols = metadata.b();

		if (numDims == 0) {
			
			System.out.println("Could not find any dimension columns in input file data!");
			
			return null;
		}

		if (numDecimalCols == 0) {
			
			System.out.println("Could not find any data columns in input file data!");
			
			return null;
		}

		IntegerIndex minDims = metadata.c();
		
		IntegerIndex maxDims = metadata.d();
		
		U val = alg.construct();
		
		FileReader fr = null;
		
		BufferedReader br = null;
		
		try {

			long[] dims = new long[numDims];
			
			for (int i = 0; i < numDims; i++) {
				
				dims[i] = maxDims.get(i) - minDims.get(i) + 1;
			}
			
			DimensionedDataSource<U> data = DimensionedStorage.allocate(val, dims);
			
			fr = new FileReader(filename);
			
			br = new BufferedReader(fr);
			
			double[] doubleVals = new double[val.componentCount()];
			
			IntegerIndex coord = new IntegerIndex(numDims);
			
			IntegerIndex fixedCoord = new IntegerIndex(numDims);
			
			while (br.ready()) {
				
				String line = br.readLine();
				
				String[] terms = line.trim().split("\\s+");

				for (int i = 0; i < numDims; i++) {

					long dimVal = Long.parseLong(terms[i]);
					
					coord.set(i, dimVal);
				}
				
				for (int i = 0; i < Math.min(val.componentCount(), numDecimalCols); i++) {
					
					doubleVals[i] = Double.parseDouble(terms[numDims + i]);
				}

				for (int i = Math.min(val.componentCount(), numDecimalCols); i < val.componentCount(); i++) {

					doubleVals[i] = 0;
				}
				
				val.setFromDoubles(doubleVals);

				for (int i = 0; i < numDims; i++) {
					
					long fixedVal = coord.get(i) - minDims.get(i);
					
					fixedCoord.set(i, fixedVal);
				}

				data.set(fixedCoord, val);
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
		
		Tuple4<Integer,Integer,IntegerIndex,IntegerIndex> fileMetaData =
				PipeToTextReader.readMetadata(filename);
		
		int numDecimalCols = fileMetaData.b();

		if (numDecimalCols < 1 || numDecimalCols > 8) {

			throw
				new IllegalArgumentException(
						"text file must have data column count between 1 and 8");
		}
		else if (numDecimalCols <= 1) {
			
			bundle.dbls.add( readDouble(filename) );
		}
		else if (numDecimalCols <= 2) {
			
			bundle.cdbls.add( readComplexDouble(filename) );
		}
		else if (numDecimalCols <= 4) {
			
			bundle.qdbls.add( readQuaternionDouble(filename) );
		}
		else {  // if here it must be between 5 and 8 components
			
			bundle.odbls.add( readOctonionDouble(filename) );
		}
		
		return bundle;
	}
}
