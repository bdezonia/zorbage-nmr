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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

import nom.bdezonia.zorbage.algebra.Algebra;
import nom.bdezonia.zorbage.algebra.G;
import nom.bdezonia.zorbage.data.NdData;
import nom.bdezonia.zorbage.datasource.IndexedDataSource;
import nom.bdezonia.zorbage.metadata.MetaDataStore;
import nom.bdezonia.zorbage.misc.DataBundle;
import nom.bdezonia.zorbage.misc.DataSourceUtils;
import nom.bdezonia.zorbage.misc.LongUtils;
import nom.bdezonia.zorbage.sampling.IntegerIndex;
import nom.bdezonia.zorbage.sampling.SamplingCartesianIntegerGrid;
import nom.bdezonia.zorbage.sampling.SamplingIterator;
import nom.bdezonia.zorbage.storage.Storage;
import nom.bdezonia.zorbage.tuple.Tuple2;
import nom.bdezonia.zorbage.tuple.Tuple4;
import nom.bdezonia.zorbage.tuple.Tuple5;
import nom.bdezonia.zorbage.type.complex.float32.ComplexFloat32Member;
import nom.bdezonia.zorbage.type.quaternion.float32.QuaternionFloat32Member;
import nom.bdezonia.zorbage.type.real.float32.Float32Member;

/**
 * 
 * @author Barry DeZonia
 *
 */
@SuppressWarnings("unused")
public class NmrPipeReader {

	public static void main(String[] args) {
		
		//DataBundle bundle = NmrPipeReader.open("/home/bdz/dev/zorbage-nmr/CC_50ms.ft2");
		//DataBundle bundle = NmrPipeReader.open("/home/bdz/dev/zorbage-nmr/CC_50ms-short.ft2");
		//DataBundle bundle = NmrPipeReader.open("/home/bdz/dev/zorbage-nmr/data.ft2");
		
		DataBundle bundle = NmrPipeReader.readAllDatasets("/home/bdz/dev/zorbage-nmr/C50C50C_1.ft123");
		
	}

	private static int HEADER_ENTRIES = 512;   // 512 floats
	private static int HEADER_BYTE_SIZE = HEADER_ENTRIES * 4;
	
	// do not instantiate
	
	private NmrPipeReader() { }
	
	/**
	 * 
	 * @param filename
	 * @return
	 */
	public static DataBundle readAllDatasets(String filename) {
		
		long numFloats = preprocessFile(filename);
		
		DataBundle bundle = new DataBundle();

		Tuple5<String, Integer, long[], IndexedDataSource<Float32Member>, MetaDataStore>
		
			data = readFloats(filename, numFloats);

		if (data.a().equals("real")) {

			NdData<Float32Member> nd = realDataSource(data.b(), data.c(), data.d(), data.e());

			nd.setSource(filename);
			
			bundle.flts.add(nd);
		}
		else if (data.a().equals("complex")) {
			
			NdData<ComplexFloat32Member> nd = complexDataSource(data.b(), data.c(), data.d(), data.e());

			nd.setSource(filename);
			
			bundle.cflts.add(nd);
		}
		else if (data.a().equals("quaternion")) {
			
			NdData<QuaternionFloat32Member> nd = quaternionDataSource(data.b(), data.c(), data.d(), data.e());

			nd.setSource(filename);
			
			bundle.qflts.add(nd);
		}
		else if (data.a().equals("point")) {
			
			// TODO: I think it is impossible to get here but not sure. As far as
			//   I can tell this is only possible if data is 4d and all axes are
			//   freq dims. But nmrpipe code seems to imply only y/z/a can be freq.
			
			throw new IllegalArgumentException("Not yet supporting "+data.b()+" dim point data");
		}
		else
			throw new IllegalArgumentException("Unknown output data type: "+data.a());
		
		return bundle;
	}
	
	private static long preprocessFile(String filename) {
		
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
		
		if ((fileLength % 4) != 0) {
			
			throw new IllegalArgumentException("file cannot be evenly divided into floats");
		}
		
		long numFloats = (fileLength - HEADER_BYTE_SIZE) / 4;

		return numFloats;
	}

	private static Tuple5<String, Integer, long[], IndexedDataSource<Float32Member>, MetaDataStore>
		readFloats(String filename, long numFloats)
	{
		IndexedDataSource<Float32Member> data =
				Storage.allocate(G.FLT.construct(), numFloats);

		File file = new File(filename);
		
		FileInputStream fis = null;

		BufferedInputStream bis = null;

		DataInputStream dis = null;
		
		try {
			
			fis = new FileInputStream(file);

			bis = new BufferedInputStream(fis);

			dis = new DataInputStream(bis);

			NmrPipeFileReader reader = new NmrPipeFileReader();

			reader.readHeader(dis);

			Float32Member type = G.FLT.construct();
			
			for (long i = 0; i < numFloats; i++) {
			
				float val = reader.nextDataFloat(dis);
				
				type.setV(val);
				
				data.set(i, type);
			}

			System.out.println();

			System.out.println("more file data? "+fis.getChannel().position()+ " " + file.length());
			
			System.out.println("raw floats read = " + numFloats);
			
			long[] dims = reader.findDims();
			
			System.out.println("raw dims = " + Arrays.toString(dims));
			
			Tuple2<String,Integer> dataType = reader.findDataType();
			
			System.out.println("data type will be = " + dataType.a());

			System.out.println("data type has components = " + dataType.b());

			System.out.println();
			
			System.out.println("some metadata follows...");
			
			System.out.println();
			
			System.out.println("user name:   " + reader.userName());
			System.out.println("oper name:   " + reader.operatorName());
			System.out.println("source:      " + reader.sourceName());
			System.out.println("title:       " + reader.title());
			System.out.println("comment:     " + reader.comment());
			System.out.println("dim 1 label: " + reader.dim1Label());
			System.out.println("dim 2 label: " + reader.dim2Label());
			System.out.println("dim 3 label: " + reader.dim3Label());
			System.out.println("dim 4 label: " + reader.dim4Label());
			
			MetaDataStore metadata = new MetaDataStore();

			metadata.putString("username", reader.userName());
			metadata.putString("operator", reader.operatorName());
			metadata.putString("source", reader.sourceName());
			metadata.putString("title", reader.title());
			metadata.putString("comment", reader.comment());
			metadata.putString("dim 1 label", reader.dim1Label());
			metadata.putString("dim 2 label", reader.dim2Label());
			metadata.putString("dim 3 label", reader.dim3Label());
			metadata.putString("dim 4 label", reader.dim4Label());
			
			return new Tuple5<>(dataType.a(), dataType.b(), dims, data, metadata);
			
		} catch (IOException e) {
			
			throw new IllegalArgumentException("IOException during file read! "+e.getMessage());
			
		} finally {

			try {
				
				if (dis != null) dis.close();
				
				if (bis != null) bis.close();

				if (fis != null) fis.close();
				
			} catch (Exception e) {
				
				;
			}
		}
	}

	private static NdData<Float32Member> realDataSource(int numComponents, long[] rawDims, IndexedDataSource<Float32Member> numbers, MetaDataStore metadata) {

		if (numComponents != 1 || (numbers.size() % numComponents) != 0)
			throw new IllegalArgumentException("suspicious input to real data source allocation routine");
		
		// from NMRPipe's fdatap.h header file:
		
		// 1D Real Format File, N Real Points:
		// 
		//   (2048-byte FDATA file header)
		//   (N four-byte float values for Real Part)
		//

		// TODO my code extends naturally to 2d, 3d, and 4d. Do I need
		// special case code cuz 3 d and 4 d data might live in files?
		// Or maybe zorbage axis order is different from NMRPipe order
		// and the values could be scrambled.
		
		NdData<Float32Member> nd = new NdData<>(rawDims, numbers);
		
		nd.metadata().merge(metadata);

		flipAroundY(G.FLT, nd);
		
		System.out.println();
		
		System.out.println("final type is real");

		System.out.println("final number of floats = " + numbers.size());
		
		System.out.println("final dims = " + Arrays.toString(rawDims));
		
		return nd;
	}
	
	private static NdData<ComplexFloat32Member> complexDataSource(int numComponents, long[] rawDims, IndexedDataSource<Float32Member> numbers, MetaDataStore metadata) {

		if (numComponents != 2 || (numbers.size() % numComponents) != 0)
			throw new IllegalArgumentException("suspicious input to complex data source allocation routine");

		IndexedDataSource<ComplexFloat32Member> complexes =
				Storage.allocate(G.CFLT.construct(), numbers.size()/numComponents);
		
		ComplexFloat32Member complex = G.CFLT.construct();
		
		Float32Member value = G.FLT.construct();

		long[] dims = rawDims.clone();
		
		// due to earlier code calls dims guaranteed to be between 1 and 4
		
		if (rawDims.length == 1 || rawDims.length == 2) {

			// from NMRPipe's fdatap.h header file:
			
			// 1D Complex Format File, N Complex Points:
			//
			//   (2048-byte FDATA file header)
			//   (N four-byte Float Values for Real Part)
			//   (N four-byte Float Values for Imag Part)
			//
			
			long n = 0;
			
			long numY = rawDims.length == 1 ? 1 : rawDims[1];  // I'm extending to 2d
			
			for (long y = 0; y < numY; y++) {

				complex.setR(0);
				
				complex.setI(0);
				
				// read the R values

				for (long k = 0; k < complexes.size(); k++) {
					
					numbers.get(n++, value);
					
					complex.setR(value);
					
					complexes.set(k, complex);
				}
		
				// read the I values
				
				for (long k = 0; k < complexes.size(); k++) {
		
					complexes.get(k, complex);
					
					numbers.get(n++, value);
					
					complex.setI(value);
					
					complexes.set(k, complex);
				}
			}
			
			// adjust the dims
			
			if (rawDims.length == 1)
				dims[0] /= 2;
			else
				dims[1] /= 2;
		}
		else {
			
			// TODO read the data in the correct interleaved way
			
			// num dims == 3 or 4: data must be read from additional files.
			//   Although maybe nmrpipe supports a 3d or 4d all in one file facility.
			
			// adjust the dims
			
			dims[1] /= 2;  // TODO: is this index right?

			throw new IllegalArgumentException("complex 3d or 4d case not yet implemented");
		}
		
		NdData<ComplexFloat32Member> nd = new NdData<>(dims, complexes);
		
		nd.metadata().merge(metadata);
		
		flipAroundY(G.CFLT, nd);
		
		System.out.println();
		
		System.out.println("final type is complex");

		System.out.println("final number of complexes = " + complexes.size());
		
		System.out.println("final dims = " + Arrays.toString(dims));
		
		return nd;
	}

	private static NdData<QuaternionFloat32Member> quaternionDataSource(int numComponents, long[] rawDims, IndexedDataSource<Float32Member> numbers, MetaDataStore metadata) {

		if (numComponents != 4 || (numbers.size() % numComponents) != 0)
			throw new IllegalArgumentException("suspicious input to quaternion data source allocation routine");

		IndexedDataSource<QuaternionFloat32Member> data = Storage.allocate(G.QFLT.construct(), numbers.size() / 4);

		IndexedDataSource<QuaternionFloat32Member> quats =
				Storage.allocate(G.QFLT.construct(), numbers.size()/numComponents);
		
		QuaternionFloat32Member quat = G.QFLT.construct();
		
		Float32Member value = G.FLT.construct();
		
		long[] dims = rawDims.clone();

		// due to earlier code calls dims guaranteed to be between 1 and 4
		
		if (rawDims.length == 1 || rawDims.length == 2) {
			
			// from NMRPipe's fdatap.h header file:
			
			// 2D Hypercomplex Plane File;
			// X-Axis N Complex Points and Y-Axis M Complex points:
			// 
			//   (2048-byte FDATA file header)
			//   (N X-Axis=Real Values for Y-Axis Increment 1 Real)
			//   (N X-Axis=Imag Values for Y-Axis Increment 1 Real)
			//   (N X-Axis=Real Values for Y-Axis Increment 1 Imag)
			//   (N X-Axis=Imag Values for Y-Axis Increment 1 Imag)
			//   (N X-Axis=Real Values for Y-Axis Increment 2 Real)
			//   (N X-Axis=Imag Values for Y-Axis Increment 2 Real)
			//   (N X-Axis=Real Values for Y-Axis Increment 2 Imag)
			//   (N X-Axis=Imag Values for Y-Axis Increment 2 Imag)
			//   ...
			//   (N X-Axis=Real Values for Y-Axis Increment M Real)
			//   (N X-Axis=Imag Values for Y-Axis Increment M Real)
			//   (N X-Axis=Real Values for Y-Axis Increment M Imag)
			//   (N X-Axis=Imag Values for Y-Axis Increment M Imag)
		
			long n = 0;
			
			long numY = rawDims.length == 1 ? 1 : rawDims[1];  // I'm extending 2d to 1d
			
			for (long y = 0; y < numY; y++) {

				quat.setR(0);
				
				quat.setI(0);

				quat.setJ(0);
				
				quat.setK(0);
				
				// read R values
				
				for (long i = 0; i < rawDims[0]; i++) {
					
					numbers.get(n++,  value);
					
					quat.setR(value);
					
					quats.set(i, quat);
				}
				
				// read I values
				
				for (long i = 0; i < rawDims[0]; i++) {
	
					quats.get(i, quat);
					
					numbers.get(n++,  value);
					
					quat.setI(value);
					
					quats.set(i, quat);
				}
				
				// read J values
				
				for (long i = 0; i < rawDims[0]; i++) {
					
					quats.get(i, quat);
					
					numbers.get(n++,  value);
					
					quat.setJ(value);
					
					quats.set(i, quat);
				}
				
				// read K values
				
				for (long i = 0; i < rawDims[0]; i++) {
					
					quats.get(i, quat);
					
					numbers.get(n++,  value);
					
					quat.setK(value);
					
					quats.set(i, quat);
				}
			}
			
			// adjust the dims
			
			if (rawDims.length == 1)
				dims[0] /= 4;
			else
				dims[1] /= 4;
		}
		else {
			
			// TODO read the data in the correct interleaved way
			
			// num dims == 3 or 4: data must be read from additional files.
			//   Although maybe nmrpipe supports a 3d or 4d all in one file facility.
			
			// adjust the dims
			
			dims[1] /= 4;  // TODO: is this index right?

			throw new IllegalArgumentException("quaternion 3d or 4d case not yet implemented");
		}
		
		NdData<QuaternionFloat32Member> nd = new NdData<>(dims, data);
		
		nd.metadata().merge(metadata);
		
		flipAroundY(G.QFLT, nd);
		
		System.out.println();
		
		System.out.println("final type is quaternion");

		System.out.println("final number of quats = " + data.size());
		
		System.out.println("final dims = " + Arrays.toString(dims));
		
		return nd;
	}
	
	private static <T extends Algebra<T,U>, U>
	
		void flipAroundY(T algebra, NdData<U> data)
	
	{
		long[] dims = DataSourceUtils.dimensions(data);
		
		if (dims.length < 2)
			return;
		
		long mid = dims[1] / 2;
		
		long[] halfDims = dims.clone();
		
		halfDims[1] = mid;
		
		U firstVal = algebra.construct();
		
		U secondVal = algebra.construct();
		
		IntegerIndex index1 = new IntegerIndex(dims.length);
		
		IntegerIndex index2 = new IntegerIndex(dims.length);
		
		SamplingCartesianIntegerGrid sampling =
				
			new SamplingCartesianIntegerGrid(halfDims);
		
		SamplingIterator<IntegerIndex> iter = sampling.iterator();
		
		while (iter.hasNext()) {
			
			iter.next(index1);
			
			index2.set(index1);
			
			long y = index1.get(1);
			
			long newY = dims[1] - y - 1;
			
			index2.set(1, newY);
			
			data.get(index1, firstVal);
			
			data.get(index2, secondVal);

			data.set(index2, firstVal);
			
			data.set(index1, secondVal);
		}
	}

	private static class NmrPipeFileReader {

		private int[] vars = new int[HEADER_ENTRIES];
		
		private boolean byteSwapNeeded = false;

		void readHeader(DataInputStream dis) throws IOException {

			for (int i = 0; i < vars.length; i++) {
				
				vars[i] = dis.readInt();
			}

			if (vars[FDMAGIC] != 0) {
				
				throw new IllegalArgumentException("This does not appear to be a nmrPipe file");
			}
			
			/* 1-26-23: personal communication with Frank D: no need to check this.
			
				if (vars[FDFLTFORMAT] != (float) 0xeeeeeeee) {
			
					throw new IllegalArgumentException(
							"This reader only supports IEEE floating point format values");
				}
			*/
			
			// endian check from appropriate header variable
			
			float headerVal = Float.intBitsToFloat(vars[FDFLTORDER]);
			
			if (Float.isNaN(headerVal))
				throw new IllegalArgumentException("Weird value present for FDFLTORDER");
			
			byteSwapNeeded =  Math.abs(headerVal - 2.345f) > 1e-6;
		}
		
		// maybe the header vars are never ints but always floats. the little docs I've seen
		//   seem to imply that. if so then this routine is not needed.
		
		int getHeaderInt(int index) {
			
			int bits = intBits(vars[index]);

			return bits;
		}
		
		float getHeaderFloat(int index) {

			int bits = intBits(vars[index]);

			return Float.intBitsToFloat(bits);
		}
		
		float nextDataFloat(DataInputStream dis) throws IOException {
			
			int bits = intBits(dis.readInt());

			return Float.intBitsToFloat(bits);
		}

		private int intBits(int bits) {

			if (byteSwapNeeded) {
				
				bits = swapInt(bits);
			}
			
			return bits;
		}
		
		private int swapInt(int bits) {
			
			int a = (bits >> 24) & 0xff;
			int b = (bits >> 16) & 0xff;
			int c = (bits >> 8)  & 0xff;
			int d = (bits >> 0)  & 0xff;
			
			bits = (d << 24) | (c << 16) | (b << 8) | (a << 0);
			
			return bits;
		}

		// TODO: this is not based on DIMORDER. It always does raw XY dims
		//   for planes. I might need to support XZ and XA (and even others?)
		//   as well.
		
		private Tuple2<String,Integer> findDataType() {
		
			int dimCount = (int) getHeaderFloat(FDDIMCOUNT);
			
			if (dimCount < 1 || dimCount > 4)
 				throw new IllegalArgumentException("dim count looks crazy "+dimCount);
			
			int numComponents = 1;

			if (dimCount >= 1)
				numComponents *= (getHeaderFloat(FDF2QUADFLAG) == 1 ? 1 : 2);

			if (dimCount >= 2)
				numComponents *= (getHeaderFloat(FDF1QUADFLAG) == 1 ? 1 : 2);

/*
			if (dimCount >= 3)
				numComponents *= (getHeaderFloat(FDF3QUADFLAG) == 1 ? 1 : 2);

			if (dimCount >= 4)
				numComponents *= (getHeaderFloat(FDF4QUADFLAG) == 1 ? 1 : 2);
*/
			
			// TODO: do I want all of these cases below to return ("point",numComponents)
			
			if (numComponents == 1) {
				return new Tuple2<>("real", numComponents);
			}
			
			if (numComponents == 2) {
				return new Tuple2<>("complex", numComponents);
			}
			
			if (numComponents == 4) {
				return new Tuple2<>("quaternion", numComponents);
			}

			return new Tuple2<>("point", numComponents);
		}

		// TODO: comparing to nmr pipe showhdr command I am assigning x, y, and z
		//   dims correctly. But I am not yet positive in which order the values
		//   should be read from the file. Read Frank's code and nmrglue's code to
		//   figure out what is best. I am always reading in x-y-z priority. Maybe
		//   Frank's code uses DIMORDER flags instead. If so I could use set/get:
		//   set/get(dimorder(1,x,y,z), dimorder(2,x,y,z), dimorder(3,x,y,z), value).
		//   Or I could do a DimensionalPermutation on the just read data to get it
		//   ordered in x-y-z-a order.
		
		// based on ideas I saw in nmrglue: maybe not what I want.
		//   perhaps we should call findDataType() first and reason
		//   more nicely about what actual dims are rather than these
		//   hacky calcs.
		
		private long[] findDims() {
			
			int dimCount = (int) getHeaderFloat(FDDIMCOUNT);
			
			if (dimCount < 1 || dimCount > 4)
 				throw new IllegalArgumentException("dim count looks crazy "+dimCount);
			
			if (dimCount == 1) {
				
				// one and two interchanged like nmrpipe code

				final int floatsPerRecord = (getHeaderFloat(FDF2QUADFLAG) == 1) ? 1 : 2;
				
				long xDim = ((long) getHeaderFloat(FDSIZE)) * floatsPerRecord;
				
				return new long[] {xDim};
			}
			else { // dimCount > 1

				final int floatsPerRecord;
				
				final long xDim, yDim, zDim, aDim;
				
				if (getHeaderFloat(FDF1QUADFLAG) == 1 && getHeaderFloat(FDTRANSPOSED) == 1) {
					
					floatsPerRecord = 1;
				}
				else if (getHeaderFloat(FDF2QUADFLAG) == 1 && getHeaderFloat(FDTRANSPOSED) == 0) {
					
					floatsPerRecord = 1;
				}
				else {
					floatsPerRecord = 2;
				}
				
				xDim = ((long) getHeaderFloat(FDSIZE)) * floatsPerRecord;
				
				if (getHeaderFloat(FDQUADFLAG) == 0 && floatsPerRecord == 1) {
				
					yDim = 2 * ((long) getHeaderFloat(FDSPECNUM));
				}
				else {

					yDim = ((long) getHeaderFloat(FDSPECNUM));
				}
				
				if (getHeaderFloat(FDDIMCOUNT) == 3 && getHeaderFloat(FDPIPEFLAG) != 0) {
					
					zDim = (long) getHeaderFloat(FDF3SIZE);
					
					return new long[] {xDim, yDim, zDim};
				}

				if (getHeaderFloat(FDDIMCOUNT) == 4 && getHeaderFloat(FDPIPEFLAG) != 0) {
					
					zDim = (long) getHeaderFloat(FDF3SIZE);
					
					aDim = (long) getHeaderFloat(FDF4SIZE);

					return new long[] {xDim, yDim, zDim, aDim};
				}
				
				return new long[] {xDim, yDim};
			}
		}
		
		private String intsToString(int startIndex, int intCount) {
			
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < intCount; i++) {
				int num = vars[startIndex+i];
				int b0 = (num >> 24) & 0xff;
				int b1 = (num >> 16) & 0xff;
				int b2 = (num >>  8) & 0xff;
				int b3 = (num >>  0) & 0xff;
				if (b0 == 0) return b.toString();
				b.append((char) b0);
				if (b1 == 0) return b.toString();
				b.append((char) b1);
				if (b2 == 0) return b.toString();
				b.append((char) b2);
				if (b3 == 0) return b.toString();
				b.append((char) b3);
			}
			return b.toString();
		}
		
		private String sourceName() {
			
			return intsToString(FDSRCNAME, 4);
		}
		
		private String userName() {

			return intsToString(FDUSERNAME, 4);
		}
		
		private String operatorName() {
			
			return intsToString(FDOPERNAME, 8);
		}

		private String title() {
			
			return intsToString(FDTITLE, 15);
		}

		private String comment() {
			
			return intsToString(FDCOMMENT, 40);
		}
		
		private String dim1Label() {

			// one and two interchanged like nmrpipe code

			return intsToString(FDF2LABEL, 2);  // looks backwards
		}

		private String dim2Label() {
			
			// one and two interchanged like nmrpipe code
			
			return intsToString(FDF1LABEL, 2);  // looks backwards
		}

		private String dim3Label() {
			
			return intsToString(FDF3LABEL, 2);
		}

		private String dim4Label() {
			
			return intsToString(FDF4LABEL, 2);
		}
		
		private String dimHalf0Label(int dim) {
			
			// one and two interchanged like nmrpipe code
			
			if (dim == 1)
				return intsToString(FDF2LABEL+0, 1);  // looks backwards
			if (dim == 2)
				return intsToString(FDF1LABEL+0, 1);  // looks backwards
			if (dim == 3)
				return intsToString(FDF3LABEL+0, 1);
			if (dim == 4)
				return intsToString(FDF4LABEL+0, 1);
			return "?";
		}
		
		private String dimHalf1Label(int dim) {
			
			// one and two interchanged like nmrpipe code
			
			if (dim == 1)
				return intsToString(FDF2LABEL+1, 1);  // looks backwards
			if (dim == 2)
				return intsToString(FDF1LABEL+1, 1);  // looks backwards
			if (dim == 3)
				return intsToString(FDF3LABEL+1, 1);
			if (dim == 4)
				return intsToString(FDF4LABEL+1, 1);
			return "?";
		}

		private String dimLabel(int dim) {

			// NOTE: one and two NOT interchanged on purpose
			
			if (dim == 1)
				return dim1Label();
			if (dim == 2)
				return dim2Label();
			if (dim == 3)
				return dim3Label();
			if (dim == 4)
				return dim4Label();
			return "?";
		}
		
		private int firstDimIndex() {
			
			return vars[FDDIMORDER1];  // probably X
		}
		
		private int secondDimIndex() {
			
			return vars[FDDIMORDER2];  // usually Y but also could be Z or A
		}
		
		private int thirdDimIndex() {  // usually Z
			
			return vars[FDDIMORDER3];
		}
		
		private int fourthDimIndex() {
			
			return vars[FDDIMORDER4];  // usually A
		}
		
		private int dimIndex(int dim) {
			if (dim == 1)
				return firstDimIndex();
			if (dim == 2)
				return secondDimIndex();
			if (dim == 3)
				return thirdDimIndex();
			if (dim == 4)
				return fourthDimIndex();
			return -1;
		}
		
		// Find nrmpipe .c/.h code to verify all the formats I think exist
		
	    final int FDMAGIC = 0;
	    final int FDFLTFORMAT = 1;
	    final int FDFLTORDER = 2;
	    // 3-8 = ?
	    final int FDDIMCOUNT = 9;
	    final int FDF3OBS = 10;
	    final int FDF3SW = 11;
	    final int FDF3ORIG = 12;
	    final int FDF3FTFLAG = 13;
	    final int FDPLANELOC = 14;
	    final int FDF3SIZE = 15;
	    final int FDF2LABEL = 16; // and 17: 8 ascii chars?
	    final int FDF1LABEL = 18; // and 19: 8 ascii chars?
	    final int FDF3LABEL = 20; // and 21: 8 ascii chars?
	    final int FDF4LABEL = 22; // and 23: 8 ascii chars?
	    final int FDDIMORDER1 = 24;
	    final int FDDIMORDER2 = 25;
	    final int FDDIMORDER3 = 26;
	    final int FDDIMORDER4 = 27;
	    final int FDF4OBS = 28;
	    final int FDF4SW = 29;
	    final int FDF4ORIG = 30;
	    final int FDF4FTFLAG = 31;
	    final int FDF4SIZE = 32;
	    // 33-39 = ?
	    final int FDDMXVAL = 40;
	    final int FDDMXFLAG = 41;
	    final int FDDELTATR = 42;
	    // 43-44 = ?
	    final int FDNUSDIM = 45;
	    // 46-49 = ?
	    final int FDF3APOD = 50;
	    final int FDF3QUADFLAG = 51;
	    // 52 = ?
	    final int FDF4APOD = 53;
	    final int FDF4QUADFLAG = 54;
	    final int FDF1QUADFLAG = 55;
	    final int FDF2QUADFLAG = 56;
	    final int FDPIPEFLAG = 57;
	    final int FDF3UNITS = 58;
	    final int FDF4UNITS = 59;
	    final int FDF3P0 = 60;
	    final int FDF3P1 = 61;
	    final int FDF4P0 = 62;
	    final int FDF4P1 = 63;
	    final int FDF2AQSIGN = 64;
	    final int FDPARTITION = 65;
	    final int FDF2CAR = 66;
	    final int FDF1CAR = 67;
	    final int FDF3CAR = 68;
	    final int FDF4CAR = 69;
	    final int FDUSER1 = 70;
	    final int FDUSER2 = 71;
	    final int FDUSER3 = 72;
	    final int FDUSER4 = 73;
	    final int FDUSER5 = 74;
	    final int FDPIPECOUNT = 75;
	    final int FDUSER6 = 76;
	    final int FDFIRSTPLANE = 77;
	    final int FDLASTPLANE = 78;
	    final int FDF2CENTER = 79;
	    final int FDF1CENTER = 80;
	    final int FDF3CENTER = 81;
	    final int FDF4CENTER = 82;
	    // 83-94 = ?
	    final int FDF2APOD = 95;
	    final int FDF2FTSIZE = 96;
	    final int FDREALSIZE = 97;
	    final int FDF1FTSIZE = 98;
	    final int FDSIZE = 99;
	    final int FDF2SW = 100;
	    final int FDF2ORIG = 101;
	    // 102-105 = ?
	    final int FDQUADFLAG = 106;
	    // 107 = ?
	    final int FDF2ZF = 108;
	    final int FDF2P0 = 109;
	    final int FDF2P1 = 110;
	    final int FDF2LB = 111;
	    // 112-118 = ?
	    final int FDF2OBS = 119;
	    // 120-134 = ?
	    final int FDMCFLAG = 135;
	    final int FDF2UNITS = 152;
	    final int FDNOISE = 153;
	    // 154-156 = ?
	    final int FDTEMPERATURE = 157;
	    final int FDPRESSURE = 158;
	    // 159-179 = ?
	    final int FDRANK = 180;
	    // 181-198 = ?
	    final int FDTAU = 199;
	    final int FDF3FTSIZE = 200;
	    final int FDF4FTSIZE = 201;
	    // 202-217 = ?
	    final int FDF1OBS = 218;
	    final int FDSPECNUM = 219;
	    final int FDF2FTFLAG = 220;
	    final int FDTRANSPOSED = 221;
	    final int FDF1FTFLAG = 222;
	    // 223-228 = ?
	    final int FDF1SW = 229;
	    // 230-233 = ?
	    final int FDF1UNITS = 234;
	    // 235-242 = ?
	    final int FDF1LB = 243;
	    // 244 = ?
	    final int FDF1P0 = 245;
	    final int FDF1P1 = 246;
	    final int FDMAX = 247;
	    final int FDMIN = 248;
	    final int FDF1ORIG = 249;
	    final int FDSCALEFLAG = 250;
	    final int FDDISPMAX = 251;
	    final int FDDISPMIN = 252;
	    final int FDPTHRESH = 253;
	    final int FDNTHRESH = 254;
	    // 255 = ?
	    final int FD2DPHASE = 256;
	    final int FDF2X1 = 257;
	    final int FDF2XN = 258;
	    final int FDF1X1 = 259;
	    final int FDF1XN = 260;
	    final int FDF3X1 = 261;
	    final int FDF3XN = 262;
	    final int FDF4X1 = 263;
	    final int FDF4XN = 264;
	    // 265 = ?
	    final int FDDOMINFO = 266;
	    final int FDMETHINFO = 267;
	    // 268-282 = ?
	    final int FDHOURS = 283;
	    final int FDMINS = 284;
	    final int FDSECS = 285;
	    final int FDSRCNAME = 286; // and 287 and 288 and 289: 16 ascii chars?
	    final int FDUSERNAME = 290; // and 291 and 292 and 293: 16 ascii chars?
	    final int FDMONTH = 294;
	    final int FDDAY = 295;
	    final int FDYEAR = 296;
	    final int FDTITLE = 297;  // through 311: 60 ascii chars?
	    final int FDCOMMENT = 312; // through 351: 160 ascii chars?
	    // 352-358 = ?
	    final int FDLASTBLOCK = 359;
	    final int FDCONTBLOCK = 360;
	    final int FDBASEBLOCK = 361;
	    final int FDPEAKBLOCK = 362;
	    final int FDBMAPBLOCK = 363;
	    final int FDHISTBLOCK = 364;
	    final int FD1DBLOCK = 365;
	    // 366-369 = ?
	    final int FDSCORE = 370;
	    final int FDSCANS = 371;
	    final int FDF3LB = 372;
	    final int FDF4LB = 373;
	    final int FDF2GB = 374;
	    final int FDF1GB = 375;
	    final int FDF3GB = 376;
	    final int FDF4GB = 377;
	    final int FDF2OBSMID = 378;
	    final int FDF1OBSMID = 379;
	    final int FDF3OBSMID = 380;
	    final int FDF4OBSMID = 381;
	    final int FDF2GOFF = 382;
	    final int FDF1GOFF = 383;
	    final int FDF3GOFF = 384;
	    final int FDF4GOFF = 385;
	    final int FDF2TDSIZE = 386;
	    final int FDF1TDSIZE = 387;
	    final int FDF3TDSIZE = 388;
	    final int FDF4TDSIZE = 389;
	    final int FD2DVIRGIN = 399;
	    final int FDF3APODCODE = 400;
	    final int FDF3APODQ1 = 401;
	    final int FDF3APODQ2 = 402;
	    final int FDF3APODQ3 = 403;
	    final int FDF3C1 = 404;
	    final int FDF4APODCODE = 405;
	    final int FDF4APODQ1 = 406;
	    final int FDF4APODQ2 = 407;
	    final int FDF4APODQ3 = 408;
	    final int FDF4C1 = 409;
	    // 410-412 = ?
	    final int FDF2APODCODE = 413;
	    final int FDF1APODCODE = 414;
	    final int FDF2APODQ1 = 415;
	    final int FDF2APODQ2 = 416;
	    final int FDF2APODQ3 = 417;
	    final int FDF2C1 = 418;
	    final int FDF2APODDF = 419;
	    final int FDF1APODQ1 = 420;
	    final int FDF1APODQ2 = 421;
	    final int FDF1APODQ3 = 422;
	    final int FDF1C1 = 423;
	    // 424-427 = ?
	    final int FDF1APOD = 428;
	    // 429-436 = ?
	    final int FDF1ZF = 437;
	    final int FDF3ZF = 438;
	    final int FDF4ZF = 439;
	    // 440-441 = ?
	    final int FDFILECOUNT = 442;
	    final int FDSLICECOUNT0 = 443;
	    final int FDTHREADCOUNT = 444;
	    final int FDTHREADID = 445;
	    final int FDSLICECOUNT1 = 446;
	    final int FDCUBEFLAG = 447;
	    // 448-463 = ?
	    final int FDOPERNAME = 464; // through 471: 32 ascii chars?
	    // 472-474 = ?
	    final int FDF1AQSIGN = 475;
	    final int FDF3AQSIGN = 476;
	    final int FDF4AQSIGN = 477;
	    // 478-479 = ?
	    final int FDF2OFFPPM = 480;
	    final int FDF1OFFPPM = 481;
	    final int FDF3OFFPPM = 482;
	    final int FDF4OFFPPM = 483;
	    // 484-511 = ?
	}
}
