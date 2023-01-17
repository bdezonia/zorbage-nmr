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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import nom.bdezonia.zorbage.algebra.G;
import nom.bdezonia.zorbage.data.NdData;
import nom.bdezonia.zorbage.datasource.IndexedDataSource;
import nom.bdezonia.zorbage.misc.DataBundle;
import nom.bdezonia.zorbage.storage.Storage;
import nom.bdezonia.zorbage.tuple.Tuple3;
import nom.bdezonia.zorbage.type.complex.float32.ComplexFloat32Member;
import nom.bdezonia.zorbage.type.real.float32.Float32Member;

/**
 * 
 * @author Barry DeZonia
 *
 */
public class NmrPipeReader {

	private static int HEADER_BYTE_SIZE = 2048;  // 512 floats
	
	// do not instantiate
	
	private NmrPipeReader() { }
	
	/**
	 * 
	 * @param filename
	 * @return
	 */
	public static DataBundle open(String filename) {
		
		// BDZ NOTE: for now I am assuming that *.ft3 is the right extension.
		// That might only be true for 3D data.
		
		long numFloats = getFileInfo(filename);
		
		DataBundle bundle = new DataBundle();

		Tuple3<String, long[], IndexedDataSource<Float32Member>> data =
				readFloats(filename, numFloats);

		if (data.a().equals("float32")) {

			NdData<Float32Member> nd = new NdData<>(data.b(), data.c());
			
			bundle.flts.add(nd);
		}
		else if (data.a().equals("cfloat32")) {
			
			IndexedDataSource<ComplexFloat32Member> complexes =
					Storage.allocate(G.CFLT.construct(), numFloats/2);
			
			ComplexFloat32Member c = G.CFLT.construct();
			
			Float32Member r = G.FLT.construct();
			
			Float32Member i = G.FLT.construct();
			
			for (long k = 0; k < complexes.size(); k++) {
				
				data.c().get(2*k, r);
				
				data.c().get(2*k+1, i);
				
				c.setR(r);
				
				c.setI(i);
				
				complexes.set(k, c);
			}
			
			NdData<ComplexFloat32Member> nd = new NdData<>(data.b(), complexes);
			
			bundle.cflts.add(nd);
		}
		else
			throw new IllegalArgumentException("unknown output data type: "+data.a());
		
		return bundle;
	}
	
	private static long getFileInfo(String filename) {
		
		File file = new File(filename);
		
		if (!file.exists()) {
			
			throw new IllegalArgumentException("File not found: "+filename);
		}
		
		if (!file.canRead()) {
			
			throw new IllegalArgumentException("File permissions do not allow read: "+filename);
		}

		long fileLength = file.length();
		
		if (fileLength < HEADER_BYTE_SIZE) {
			
			throw new IllegalArgumentException("File is too small to contain nrmpipe data: "+filename);
		}
		
		long numFloats = (fileLength - HEADER_BYTE_SIZE) / 4;

		return numFloats;
	}

	private static Tuple3<String, long[], IndexedDataSource<Float32Member>>
		readFloats(String filename, long numFloats)
	{
		IndexedDataSource<Float32Member> data =
				Storage.allocate(G.FLT.construct(), numFloats);

		File file = new File(filename);
		
		FileInputStream fis = null;

		DataInputStream dis = null;
		
		try {
			
			fis = new FileInputStream(file);

			dis = new DataInputStream(fis);
			
			byte[] header = new byte[HEADER_BYTE_SIZE];

			dis.read(header);

			int a = header[8];
			int b = header[9];
			int c = header[10];
			int d = header[11];
			
			float hVal =
					Float.intBitsToFloat((a << 0) | (b << 8) | (c << 16) | (d << 24));
			
			boolean byteSwapNeeded = (hVal - 2.345f > 1e-6);

			Float32Member type = G.FLT.construct();
			
			for (long i = 0; i < numFloats; i++) {
			
				float val = readFloat(dis, byteSwapNeeded);
				
				type.setV(val);
				
				data.set(i, type);
			}
			
			return new Tuple3<>("float32", new long[] {numFloats}, data);
			
		} catch (IOException e) {
			
			throw new IllegalArgumentException("IOException during file read! "+e.getMessage());
			
		} finally {

			try {
				
				if (dis != null) dis.close();
				
				if (fis != null) fis.close();
				
			} catch (Exception e) {
				
				;
			}
		}
	}

	private static float readFloat(DataInputStream dis, boolean byteSwapNeeded)
		throws IOException
	{
		float val = dis.readFloat();
		
		if (byteSwapNeeded) {
			
			int bits = Float.floatToIntBits(val);
			
			int a = (bits >> 24) & 0xff;
			int b = (bits >> 16) & 0xff;
			int c = (bits >> 8)  & 0xff;
			int d = (bits >> 0)  & 0xff;
			
			bits = (d << 24) | (c << 16) | (b << 8) | (a << 0);
			
			val = Float.intBitsToFloat(bits);
		}
		
		return val;
	}

}
