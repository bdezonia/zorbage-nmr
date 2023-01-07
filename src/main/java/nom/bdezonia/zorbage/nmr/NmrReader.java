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

import nom.bdezonia.zorbage.algebra.G;
import nom.bdezonia.zorbage.data.DimensionedDataSource;
import nom.bdezonia.zorbage.misc.DataBundle;
import nom.bdezonia.zorbage.tuple.Tuple5;
import nom.bdezonia.zorbage.type.complex.float64.ComplexFloat64Member;
import nom.bdezonia.zorbage.type.octonion.float64.OctonionFloat64Member;
import nom.bdezonia.zorbage.type.quaternion.float64.QuaternionFloat64Member;
import nom.bdezonia.zorbage.type.real.float64.Float64Member;

/**
 * 
 * @author Barry DeZonia
 *
 */
public class NmrReader {

	// do not instantiate
	
	private NmrReader() { }

	/**
	 * 
	 * @param filename
	 * @return
	 */
	public static DataBundle open(String filename) {
		
		Tuple5<Integer,Long,Long,Long,Long> fileMetaData =
				TwoDText.metadata(filename);
		
		int numRealColumns = fileMetaData.a();

		if (numRealColumns <= 0 || numRealColumns > 8) {

			throw
				new IllegalArgumentException(
						"text file must have real data column count between 1 and 8");
		}
		else if (numRealColumns <= 1) {
			
			return openDouble(filename);
		}
		else if (numRealColumns <= 2) {
			
			return openComplexDouble(filename);
		}
		else if (numRealColumns <= 4) {
			
			return openQuaternionDouble(filename);
		}
		else {  // if here it must be between 5 and 8 cols
			
			return openOctonionDouble(filename);
		}
	}
	
	/**
	 * 
	 * @param filename
	 * @return
	 */
	public static DataBundle openDouble(String filename) {
		
		DataBundle bundle = new DataBundle();
		
		DimensionedDataSource<Float64Member> data = TwoDText.read(filename, G.DBL);
		
		bundle.dbls.add(data);
		
		return bundle;
	}
	/**
	 * 
	 * @param filename
	 * @return
	 */
	public static DataBundle openComplexDouble(String filename) {
		
		DataBundle bundle = new DataBundle();
		
		DimensionedDataSource<ComplexFloat64Member> data = TwoDText.read(filename, G.CDBL);
		
		bundle.cdbls.add(data);
		
		return bundle;
	}
	/**
	 * 
	 * @param filename
	 * @return
	 */
	public static DataBundle openQuaternionDouble(String filename) {
		
		DataBundle bundle = new DataBundle();
		
		DimensionedDataSource<QuaternionFloat64Member> data = TwoDText.read(filename, G.QDBL);
		
		bundle.qdbls.add(data);
		
		return bundle;
	}
	/**
	 * 
	 * @param filename
	 * @return
	 */
	public static DataBundle openOctonionDouble(String filename) {
		
		DataBundle bundle = new DataBundle();
		
		DimensionedDataSource<OctonionFloat64Member> data = TwoDText.read(filename, G.ODBL);
		
		bundle.odbls.add(data);
		
		return bundle;
	}
}
