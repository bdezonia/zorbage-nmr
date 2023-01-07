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
		
		DataBundle bundle = new DataBundle();
		
		// TODO: detect the number of real components per line in the file
		//   and then based upon that make real/comp/quat/oct data source.
		
		// for now just do doubles
		
		DimensionedDataSource<Float64Member> data = TwoDText.read(filename, G.DBL);
		
		bundle.dbls.add(data);
		
		return bundle;
	}
}
