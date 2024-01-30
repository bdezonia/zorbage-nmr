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
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import nom.bdezonia.zorbage.algebra.Algebra;
import nom.bdezonia.zorbage.algebra.G;
import nom.bdezonia.zorbage.data.NdData;
import nom.bdezonia.zorbage.datasource.IndexedDataSource;
import nom.bdezonia.zorbage.metadata.MetaDataStore;
import nom.bdezonia.zorbage.misc.DataBundle;
import nom.bdezonia.zorbage.misc.DataSourceUtils;
import nom.bdezonia.zorbage.sampling.IntegerIndex;
import nom.bdezonia.zorbage.sampling.SamplingCartesianIntegerGrid;
import nom.bdezonia.zorbage.sampling.SamplingIterator;
import nom.bdezonia.zorbage.storage.Storage;
import nom.bdezonia.zorbage.tuple.Tuple2;
import nom.bdezonia.zorbage.tuple.Tuple5;
import nom.bdezonia.zorbage.type.complex.float32.ComplexFloat32Member;
import nom.bdezonia.zorbage.type.quaternion.float32.QuaternionFloat32Member;
import nom.bdezonia.zorbage.type.real.float32.Float32Member;

/**
 * 
 * @author Barry DeZonia
 *
 */
public class NmrPipeReader {

	private static int HEADER_ENTRIES = 512;   // 512 floats
	private static int HEADER_BYTE_SIZE = HEADER_ENTRIES * 4;
	
	// do not instantiate
	
	private NmrPipeReader() { }
	
	/**
	 * 
	 * @param filename
	 * @return
	 */
	public static
	
		DataBundle
		
			readAllDatasets(String filename)
	{
		try {
		
			URI uri = new URI("file", null, new File(filename).getAbsolutePath(), null);
			
			return readAllDatasets(uri);
	
		} catch (URISyntaxException e) {
			
			throw new IllegalArgumentException("Bad name for file: "+e.getMessage());
		}
	}

	/**
	 * 
	 * @param filename
	 * @return
	 */
	public static
	
		DataBundle
			
			readAllDatasets(URI fileURI)
	{
		long numFloats = preprocessFile(fileURI);
		
		DataBundle bundle = new DataBundle();

		Tuple5<String, Integer, long[], IndexedDataSource<Float32Member>, MetaDataStore>
		
			data = readFloats(fileURI, numFloats);

		if (data.a().equals("real")) {

			NdData<Float32Member> nd = realDataSource(data.b(), data.c(), data.d(), data.e());

			nd.setSource(fileURI.toString());
			
			bundle.flts.add(nd);
		}
		else if (data.a().equals("complex")) {
			
			NdData<ComplexFloat32Member> nd = complexDataSource(data.b(), data.c(), data.d(), data.e());

			nd.setSource(fileURI.toString());
			
			bundle.cflts.add(nd);
		}
		else if (data.a().equals("quaternion")) {
			
			NdData<QuaternionFloat32Member> nd = quaternionDataSource(data.b(), data.c(), data.d(), data.e());

			nd.setSource(fileURI.toString());
			
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
	
	/**
	 * 
	 * @param fileURI
	 * @return
	 */
	private static
	
		long
		
			preprocessFile(URI fileURI)
	{
		try {
			
			InputStream is = fileURI.toURL().openStream();
			
			if (is == null) {
				
				throw new IllegalArgumentException("Data not found: "+fileURI);
			}
			
			long fileLength = 0;
			
			while (is.read() != -1) fileLength++;
			
			is.close();

			
			if (fileLength < HEADER_BYTE_SIZE) {
				
				throw new IllegalArgumentException("Source is too small to contain nrmpipe data: "+fileURI);
			}
			
			if ((fileLength % 4) != 0) {
				
				throw new IllegalArgumentException("Source cannot be evenly divided into floats");
			}
			
			long numFloats = (fileLength - HEADER_BYTE_SIZE) / 4;

			return numFloats;

		} catch (IOException e) {
		
			throw new IllegalArgumentException("Error: "+e.getMessage());
		}
	}

	/**
	 * 
	 * @param fileURI
	 * @param numFloats
	 * @return
	 */
	private static
	
		Tuple5<String, Integer, long[], IndexedDataSource<Float32Member>,
				MetaDataStore>
	
			readFloats(URI fileURI, long numFloats)
	{
		IndexedDataSource<Float32Member> data =
				Storage.allocate(G.FLT.construct(), numFloats);

		InputStream is = null;

		BufferedInputStream bis = null;

		DataInputStream dis = null;
		
		try {
			
			is = fileURI.toURL().openStream();

			bis = new BufferedInputStream(is);

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

			System.out.println("raw floats read = " + numFloats);
			
			long[] dims = reader.findDims();
			
			System.out.println("raw dims = " + Arrays.toString(dims));
			
			Tuple2<String,Integer> dataType = reader.findDataType();
			
			System.out.println("data type will be = " + dataType.a());

			System.out.println("data type has components = " + dataType.b());

			System.out.println();
			
			MetaDataStore metadata = new MetaDataStore();

			metadata.putString("username", reader.userName());
			metadata.putString("operator", reader.operatorName());
			metadata.putString("source", reader.sourceName());
			metadata.putString("title", reader.title());
			metadata.putString("comment", reader.comment());
			
			for (int i = 0; i < 4; i++) {
				metadata.putString("dim " + i + " label",  reader.dimLabel(i));
				metadata.putString("dim " + i + " unit",   reader.unit(i));
				metadata.putFloat( "dim " + i + " offset", reader.offset(i));
				metadata.putFloat( "dim " + i + " sweep width", reader.sweepWidth(i));
				metadata.putFloat( "dim " + i + " obs freq",    reader.obsFreq(i));
			}
			
			return new Tuple5<>(dataType.a(), dataType.b(), dims, data, metadata);
			
		} catch (IOException e) {
			
			throw new IllegalArgumentException("IOException during data read! "+e.getMessage());
			
		} finally {

			try {
				
				if (dis != null) dis.close();
				
				if (bis != null) bis.close();

				if (is != null) is.close();
				
			} catch (Exception e) {
				
				;
			}
		}
	}

	/**
	 * 
	 * @param numComponents
	 * @param rawDims
	 * @param numbers
	 * @param metadata
	 * @return
	 */
	private static
	
		NdData<Float32Member>
	
			realDataSource(int numComponents, long[] rawDims, IndexedDataSource<Float32Member> numbers, MetaDataStore metadata)
	{
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
		
		setUnitsEtc(nd);
		
		System.out.println();
		
		System.out.println("final type is real");

		System.out.println("final number of floats = " + numbers.size());
		
		System.out.println("final dims = " + Arrays.toString(rawDims));
		
		return nd;
	}
	
	/**
	 * 
	 * @param numComponents
	 * @param rawDims
	 * @param numbers
	 * @param metadata
	 * @return
	 */
	private static
	
		NdData<ComplexFloat32Member>
	
			complexDataSource(int numComponents, long[] rawDims, IndexedDataSource<Float32Member> numbers, MetaDataStore metadata)
	{
		if (numComponents != 2 || (numbers.size() % 2) != 0)
			throw new IllegalArgumentException("suspicious input to complex data source allocation routine");

		IndexedDataSource<ComplexFloat32Member> complexes =
				
				Storage.allocate(G.CFLT.construct(), numbers.size() / 2);
		
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
			
			// adjust the dims
			
			if (rawDims.length == 1)
				dims[0] /= 2;
			else
				dims[1] /= 2;

			long n = 0;
			
			long numX = dims[0];
			
			long numY = rawDims.length == 1 ? 1 : dims[1];  // I'm extending to 2d
			
			System.out.println("floats = "+numbers.size());
			
			System.out.println("dims = "+Arrays.toString(dims));
			
			System.out.println("numY = "+numY);
			
			for (long y = 0; y < numY; y++) {

				// read R values
				
				for (long x = 0; x < numX; x++) {
					
					complexes.get(y * numX + x, complex);
					
					numbers.get(n++, value);
					
					complex.setR(value);

					complexes.set(y * numX + x, complex);
				}
				
				// read I values
				
				for (long x = 0; x < numX; x++) {
					
					complexes.get(y * numX + x, complex);
					
					numbers.get(n++, value);
					
					complex.setI(value);

					complexes.set(y * numX + x, complex);
				}
			}
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
		
		setUnitsEtc(nd);
		
		System.out.println();
		
		System.out.println("final type is complex");

		System.out.println("final number of complexes = " + complexes.size());
		
		System.out.println("final dims = " + Arrays.toString(dims));
		
		return nd;
	}

	/**
	 * 
	 * @param numComponents
	 * @param rawDims
	 * @param numbers
	 * @param metadata
	 * @return
	 */
	private static
	
		NdData<QuaternionFloat32Member>
	
			quaternionDataSource(int numComponents, long[] rawDims, IndexedDataSource<Float32Member> numbers, MetaDataStore metadata)
	{
		if (numComponents != 4 || (numbers.size() % 4) != 0)
			throw new IllegalArgumentException("suspicious input to quaternion data source allocation routine");

		IndexedDataSource<QuaternionFloat32Member> quats =

				Storage.allocate(G.QFLT.construct(), numbers.size() / 4);
		
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
			
			// adjust the dims
			
			if (rawDims.length == 1)
				dims[0] /= 4;
			else
				dims[1] /= 4;

			long n = 0;
			
			long numX = dims[0];

			long numY = rawDims.length == 1 ? 1 : dims[1];  // I'm extending 2d to 1d

			for (long y = 0; y < numY; y++) {

				// read R values
				
				for (long x = 0; x < numX; x++) {
					
					quats.get(y * numX + x, quat);
					
					numbers.get(n++, value);
					
					quat.setR(value);

					quats.set(y * numX + x, quat);
				}
				
				// read I values
				
				for (long x = 0; x < numX; x++) {
					
					quats.get(y * numX + x, quat);
					
					numbers.get(n++, value);
					
					quat.setI(value);

					quats.set(y * numX + x, quat);
				}
				
				// read J values
				
				for (long x = 0; x < numX; x++) {
					
					quats.get(y * numX + x, quat);
					
					numbers.get(n++, value);
					
					quat.setJ(value);

					quats.set(y * numX + x, quat);
				}
				
				// read K values
				
				for (long x = 0; x < numX; x++) {
					
					quats.get(y * numX + x, quat);
					
					numbers.get(n++, value);
					
					quat.setK(value);

					quats.set(y * numX + x, quat);
				}
			}
		}
		else {
			
			// TODO read the data in the correct interleaved way
			
			// num dims == 3 or 4: data must be read from additional files.
			//   Although maybe nmrpipe supports a 3d or 4d all in one file facility.
			
			// adjust the dims
			
			dims[1] /= 4;  // TODO: is this index right?

			throw new IllegalArgumentException("quaternion 3d or 4d case not yet implemented");
		}
		
		NdData<QuaternionFloat32Member> nd = new NdData<>(dims, quats);
		
		nd.metadata().merge(metadata);
		
		flipAroundY(G.QFLT, nd);
		
		setUnitsEtc(nd);
		
		System.out.println();
		
		System.out.println("final type is quaternion");

		System.out.println("final number of quats = " + quats.size());
		
		System.out.println("final dims = " + Arrays.toString(dims));
		
		return nd;
	}
	
	/**
	 * 
	 * @param <T>
	 * @param <U>
	 * @param algebra
	 * @param data
	 */
	private static
	
		<T extends Algebra<T,U>,
			U>
	
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

	/**
	 * 
	 * @param data
	 */
	private static
	
		void setUnitsEtc(NdData<?> data)
	
	{
		for (int i = 0; i < data.numDimensions(); i++) {
			
			data.setAxisType(i, data.metadata().getString("dim "+i+" label"));
			data.setAxisUnit(i, data.metadata().getString("dim "+i+" unit"));
		}
	}
	
	/**
	 * 
	 * @author bdezonia
	 *
	 */
	public static class NmrPipeFileReader {

		private int[] vars = new int[HEADER_ENTRIES];
		
		private boolean byteSwapNeeded = false;

		/**
		 * 
		 * @param dis
		 * @throws IOException
		 */
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
		
		/**
		 * 
		 * @param index
		 * @return
		 */
		int getHeaderInt(int index) {
			
			int bits = intBits(vars[index]);

			return bits;
		}

		/**
		 * 
		 * @param index
		 * @return
		 */
		float getHeaderFloat(int index) {

			int bits = intBits(vars[index]);

			return Float.intBitsToFloat(bits);
		}
		
		/**
		 * 
		 * @param dis
		 * @return
		 * @throws IOException
		 */
		float nextDataFloat(DataInputStream dis) throws IOException {
			
			int bits = intBits(dis.readInt());

			return Float.intBitsToFloat(bits);
		}

		/**
		 * 
		 * @param bits
		 * @return
		 */
		private int intBits(int bits) {

			if (byteSwapNeeded) {
				
				bits = swapInt(bits);
			}
			
			return bits;
		}
		
		/**
		 * 
		 * @param bits
		 * @return
		 */
		private int swapInt(int bits) {
			
			int a = (bits >> 24) & 0xff;
			int b = (bits >> 16) & 0xff;
			int c = (bits >> 8)  & 0xff;
			int d = (bits >> 0)  & 0xff;
			
			bits = (d << 24) | (c << 16) | (b << 8) | (a << 0);
			
			return bits;
		}


		/**
		 * 
		 * @return
		 */
		private Tuple2<String,Integer> findDataType() {
		
			int dimCount = dimCount();
			
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
		
		/**
		 * 
		 * @return
		 */
		private long[] findDims() {
			
			int dimCount = dimCount();
			
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
				
					yDim = 2 * ((long) specNum());
				}
				else {

					yDim = ((long) specNum());
				}
				
				if (dimCount() == 3 && getHeaderFloat(FDPIPEFLAG) != 0) {
					
					zDim = (long) getHeaderFloat(FDF3SIZE);
					
					return new long[] {xDim, yDim, zDim};
				}

				if (dimCount() == 4 && getHeaderFloat(FDPIPEFLAG) != 0) {
					
					zDim = (long) getHeaderFloat(FDF3SIZE);
					
					aDim = (long) getHeaderFloat(FDF4SIZE);

					return new long[] {xDim, yDim, zDim, aDim};
				}
				
				return new long[] {xDim, yDim};
			}
		}
		
		/**
		 * 
		 * @param startIndex
		 * @param intCount
		 * @return
		 */
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

		
		/**
		 * 
		 */
		int dimCount() {
			
			return (int) getHeaderFloat(FDDIMCOUNT);
		}
		
		/**
		 * 
		 */
		int fileCount() {
			
			return getHeaderInt(FDFILECOUNT);
		}
		
		/**
		 * 
		 */
		int slice0Count() {
			
			return getHeaderInt(FDSLICECOUNT0);
		}
		
		/**
		 * 
		 */
		int slice1Count() {
			
			return getHeaderInt(FDSLICECOUNT1);
		}
		
		/**
		 * 
		 */
		int cubeFlag() {
			
			return getHeaderInt(FDCUBEFLAG);
		}
		
		/**
		 * 
		 */
		int threadCount() {
			
			return getHeaderInt(FDTHREADCOUNT);
		}
		
		/**
		 * 
		 */
		int threadId() {
			
			return getHeaderInt(FDTHREADID);
		}

		/**
		 * 
		 */
		String sourceName() {
			
			return intsToString(FDSRCNAME, 4);
		}
		
		/**
		 * 
		 */
		String userName() {

			return intsToString(FDUSERNAME, 4);
		}
		
		/**
		 * 
		 */
		String operatorName() {
			
			return intsToString(FDOPERNAME, 8);
		}

		/**
		 * 
		 */
		String title() {
			
			return intsToString(FDTITLE, 15);
		}

		/**
		 * 
		 */
		String comment() {
			
			return intsToString(FDCOMMENT, 40);
		}
		
		/**
		 * 
		 * @return
		 */
		int floatFormat() {
			
			return getHeaderInt(FDFLTFORMAT);
		}
		
		/**
		 * 
		 * @return
		 */
		float floatOrder() {
			
			return getHeaderFloat(FDFLTORDER);
		}
		
		/**
		 * 
		 * @return
		 */
		int id() {
			
			return getHeaderInt(FDID);
		}

		/**
		 * 
		 * @return
		 */
		int virgin() {
			
			return getHeaderInt(FD2DVIRGIN);
		}
		
		/**
		 * 
		 * @return
		 */
		float scale() {
			
			return getHeaderFloat(FDSCALE);
		}
		
		/**
		 * 
		 * @return
		 */
		int planeLoc() {
			
			return getHeaderInt(FDPLANELOC);
		}

		/**
		 * 
		 * @return
		 */
		float apodizationDDF() { // should this be an int?
			
			return getHeaderFloat(FDF2APODDF);
		}
		
		/**
		 * 
		 * @return
		 */
		int creationYear() {
			
			return getHeaderInt(FDYEAR);
		}
		
		/**
		 * 
		 * @return
		 */
		int creationMonth() {
			
			return getHeaderInt(FDMONTH);
		}
		
		/**
		 * 
		 * @return
		 */
		int creationDay() {
			
			return getHeaderInt(FDDAY);
		}
		
		/**
		 * 
		 * @return
		 */
		int creationHour() {
			
			return getHeaderInt(FDHOURS);
		}
		
		/**
		 * 
		 * @return
		 */
		int creationMinute() {
			
			return getHeaderInt(FDMINS);
		}
		
		/**
		 * 
		 * @return
		 */
		int creationSecond() {
			
			return getHeaderInt(FDSECS);
		}

		/**
		 * 
		 * @return
		 */
		int lastBlock() {
			
			return getHeaderInt(FDLASTBLOCK);
		}
		
		/**
		 * 
		 * @return
		 */
		int contBlock() {
			
			return getHeaderInt(FDCONTBLOCK);
		}
		
		/**
		 * 
		 * @return
		 */
		int baseBlock() {
			
			return getHeaderInt(FDBASEBLOCK);
		}

		/**
		 * 
		 * @return
		 */
		int peakBlock() {
			
			return getHeaderInt(FDPEAKBLOCK);
		}
		
		/**
		 * 
		 * @return
		 */
		int bmapBlock() {
			
			return getHeaderInt(FDBMAPBLOCK);
		}

		/**
		 * 
		 * @return
		 */
		int histBlock() {
			
			return getHeaderInt(FDHISTBLOCK);
		}
		
		/**
		 * 
		 * @return
		 */
		int oneDBlock() {
			
			return getHeaderInt(FD1DBLOCK);
		}

		/**
		 * 
		 * @return
		 */
	    float fdScore() {
	    
	    	return getHeaderFloat(FDSCORE);
	    }

	    /**
	     * 
	     * @return
	     */
	    float fdScans() {
	    
	    	return getHeaderInt(FDSCANS);
	    }

	    /**
		 * 
		 * @return
		 */
		int domInfo() {  // float?
			
			return getHeaderInt(FDDOMINFO);
		}
		
		/**
		 * 
		 * @return
		 */
		int methInfo() {  // float?
			
			return getHeaderInt(FDMETHINFO);
		}
		
		/**
		 * 
		 * @return
		 */
		float twoDPhase() {
			
			return getHeaderFloat(FD2DPHASE);
		}
		
		/**
		 * 
		 * @return
		 */
		float max() {
			
			return getHeaderFloat(FDMAX);
		}
		
		/**
		 * 
		 * @return
		 */
		float min() {
			
			return getHeaderFloat(FDMIN);
		}
		
		/**
		 * 
		 * @return
		 */
		int scaleFlag() {
			
			return getHeaderInt(FDSCALEFLAG);
		}
		
		/**
		 * 
		 * @return
		 */
		float dispMin() {
			
			return getHeaderFloat(FDDISPMIN);
		}
		
		/**
		 * 
		 * @return
		 */
		float dispMax() {
			
			return getHeaderFloat(FDDISPMAX);
		}
		
		/**
		 * 
		 * @return
		 */
		float pThresh() {
			
			return getHeaderFloat(FDPTHRESH);
		}
		
		/**
		 * 
		 * @return
		 */
		float nThresh() {
			
			return getHeaderFloat(FDNTHRESH);
		}

		/**
		 * 
		 * @return
		 */
		int mcFlag() {
			
			return getHeaderInt(FDMCFLAG);
		}
		
		/**
		 * 
		 * @return
		 */
		float noise() {

			return getHeaderFloat(FDNOISE);
		}
		
		/**
		 * 
		 * @return
		 */
		float temperature() {

			return getHeaderFloat(FDTEMPERATURE);
		}
		
		/**
		 * 
		 * @return
		 */
		float pressure() {

			return getHeaderFloat(FDPRESSURE);
		}

		/**
		 * 
		 * @return
		 */
		int rank() {
			
			return getHeaderInt(FDRANK);
		}

		/**
		 * 
		 * @return
		 */
		int transposed() {
			
			return getHeaderInt(FDTRANSPOSED);
		}

		/**
		 * 
		 * @return
		 */
		int tau() {
			
			return getHeaderInt(FDTAU);
		}

		/**
		 * 
		 * @return
		 */
		float specNum() {
			
			return getHeaderFloat(FDSPECNUM);
		}

		/**
		 * 
		 * @return
		 */
		float dmxVal() {
			
			return getHeaderFloat(FDDMXVAL);
		}

		/**
		 * 
		 * @return
		 */
		int dmxFlag() {
			
			return getHeaderInt(FDDMXFLAG);
		}

		/**
		 * 
		 * @return
		 */
		float deltaTR() {
			
			return getHeaderFloat(FDDELTATR);
		}

		/**
		 * 
		 * @return
		 */
		int nusDim() {
			
			return getHeaderInt(FDNUSDIM);
		}
		
	    /**
		 * 
		 * @param dimNumber
		 * @return
		 */
		private int dimIndex(int dimNumber) {
			
			// needed to read as floats and cast as ints. this must be an ancient way
			//   to get ints from the header.
			
			if (dimNumber == 0)
				
				return (int) getHeaderFloat(FDDIMORDER1);
						
			else if (dimNumber == 1)
				
				return (int) getHeaderFloat(FDDIMORDER2);

			else if (dimNumber == 2)
				
				return (int) getHeaderFloat(FDDIMORDER3);

			else if (dimNumber == 3)
				
				return (int) getHeaderFloat(FDDIMORDER4);
			
			else
				
				throw new IllegalArgumentException("DIM NUMBER IS OUT OF BOUNDS "+dimNumber);
		}
		
		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		String dimLabel(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return intsToString(FDF1LABEL, 2);
			
			else if (dimIndex == 2)

				return intsToString(FDF2LABEL, 2);
			
			else if (dimIndex == 3)

				return intsToString(FDF3LABEL, 2);
			
			else if (dimIndex == 4)

				return intsToString(FDF4LABEL, 2);
			
			else
				
				return "unknown";
		}
		
		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		String unit(int dimNumber) {
			
			int dimIndex = dimIndex(dimNumber);

			int val = -1;

			if (dimIndex == 1)
				
				val = getHeaderInt(FDF1UNITS);
			
			else if (dimIndex == 2)
				
				val = getHeaderInt(FDF2UNITS);
			
			else if (dimIndex == 3)
				
				val = getHeaderInt(FDF3UNITS);
			
			else if (dimIndex == 4)
				
				val = getHeaderInt(FDF4UNITS);
			
			else
				
				return "illegal dimIndex";

			// NOTE this was documented in NDUNITS blurb in header. It may not actually apply.
			
			if (val == 0)
				
				return "none";
			
			else if (val == 1)
				
				return "seconds";
			
			else if (val == 2)
				
				return "hertz";
			
			else if (val == 3)
				
				return "ppm";
			
			else if (val == 4)
				
				return "pts";
			
			else
				
				return "unknown";
		}
		
		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float offset(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)
			
				return getHeaderFloat(FDF1ORIG);
			
			else if (dimIndex == 2)
				
				return getHeaderFloat(FDF2ORIG);
			
			else if (dimIndex == 3)
				
				return getHeaderFloat(FDF3ORIG);
			
			else if (dimIndex == 4)
				
				return getHeaderFloat(FDF4ORIG);
			
			else
				
				return Float.NaN;
		}
		
		/**
		 * 
		 * @param dimNumber
		 * @return Sweep width in Hz
		 */
		float sweepWidth(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1SW);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2SW);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3SW);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4SW);
			
			else
				
				return Float.NaN;
		}
		
		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float obsFreq(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1OBS);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2OBS);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3OBS);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4OBS);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float offPPM(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1OFFPPM);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2OFFPPM);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3OFFPPM);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4OFFPPM);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float ftSize(int dimNumber) {  // TODO: is this an int instead of a float?

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1FTSIZE);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2FTSIZE);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3FTSIZE);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4FTSIZE);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float tdSize(int dimNumber) {  // TODO: is this an int instead of a float?

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1TDSIZE);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2TDSIZE);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3TDSIZE);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4TDSIZE);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float gaussianOffset(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1GOFF);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2GOFF);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3GOFF);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4GOFF);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float origObsFreq(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1OBSMID);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2OBSMID);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3OBSMID);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4OBSMID);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float extraExpBroadening(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1LB);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2LB);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3LB);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4LB);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float extraGaussBroadening(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1GB);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2GB);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3GB);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4GB);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float negOfZeroFillSize(int dimNumber) {  // TODO: is this an INT instead of a FLOAT?

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1ZF);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2ZF);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3ZF);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4ZF);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		int apodization(int dimNumber) {  // TODO: should this be a float

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderInt(FDF1APOD);
			
			else if (dimIndex == 2)

				return getHeaderInt(FDF2APOD);
			
			else if (dimIndex == 3)

				return getHeaderInt(FDF3APOD);
			
			else if (dimIndex == 4)

				return getHeaderInt(FDF4APOD);
			
			else
				
				return Integer.MIN_VALUE;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		int apodizationCode(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderInt(FDF1APODCODE);
			
			else if (dimIndex == 2)

				return getHeaderInt(FDF2APODCODE);
			
			else if (dimIndex == 3)

				return getHeaderInt(FDF3APODCODE);
			
			else if (dimIndex == 4)

				return getHeaderInt(FDF4APODCODE);
			
			else
				
				return Integer.MIN_VALUE;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float apodizationParam1(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1APODQ1);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2APODQ1);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3APODQ1);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4APODQ1);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float apodizationParam2(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1APODQ2);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2APODQ2);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3APODQ2);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4APODQ2);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float apodizationParam3(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1APODQ3);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2APODQ3);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3APODQ3);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4APODQ3);
			
			else
				
				return Float.NaN;
		}
		
		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float zeroFreqLocation(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1CENTER);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2CENTER);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3CENTER);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4CENTER);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float carrierPositionPPM(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1CAR);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2CAR);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3CAR);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4CAR);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float zeroOrderPhase(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1P0);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2P0);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3P0);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4P0);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float firstOrderPhase(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1P1);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2P1);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3P1);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4P1);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		int dataTypeCode(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderInt(FDF1QUADFLAG);
			
			else if (dimIndex == 2)

				return getHeaderInt(FDF2QUADFLAG);
			
			else if (dimIndex == 3)

				return getHeaderInt(FDF3QUADFLAG);
			
			else if (dimIndex == 4)

				return getHeaderInt(FDF4QUADFLAG);
			
			else
				
				return Integer.MIN_VALUE;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		int domain(int dimNumber) {  // freq domain = 1 and time domain = 0

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderInt(FDF1FTFLAG);
			
			else if (dimIndex == 2)

				return getHeaderInt(FDF2FTFLAG);
			
			else if (dimIndex == 3)

				return getHeaderInt(FDF3FTFLAG);
			
			else if (dimIndex == 4)

				return getHeaderInt(FDF4FTFLAG);
			
			else
				
				return Integer.MIN_VALUE;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float x1(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1X1);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2X1);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3X1);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4X1);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float xN(int dimNumber) {

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1XN);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2XN);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3XN);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4XN);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float aqSign(int dimNumber) {  // TODO: should this be an int?

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1AQSIGN);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2AQSIGN);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3AQSIGN);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4AQSIGN);
			
			else
				
				return Float.NaN;
		}

		/**
		 * 
		 * @param dimNumber
		 * @return
		 */
		float c1(int dimNumber) {  // TODO: should this be an int?

			int dimIndex = dimIndex(dimNumber);
			
			if (dimIndex == 1)

				return getHeaderFloat(FDF1C1);
			
			else if (dimIndex == 2)

				return getHeaderFloat(FDF2C1);
			
			else if (dimIndex == 3)

				return getHeaderFloat(FDF3C1);
			
			else if (dimIndex == 4)

				return getHeaderFloat(FDF4C1);
			
			else
				
				return Float.NaN;
		}

		// Find nrmpipe .c/.h code to verify all the formats I think exist
		
	    final int FDMAGIC = 0;
	    final int FDFLTFORMAT = 1;
	    final int FDFLTORDER = 2;
	    final int FDID = 3;
	    // 4-8 = ?
	    final int FDDIMCOUNT = 9;
	    final int FDF3OBS = 10;
	    final int FDF3SW = 11;
	    final int FDF3ORIG = 12;  // NDORIG is in hz so maybe this is too
	    final int FDF3FTFLAG = 13;
	    final int FDPLANELOC = 14;
	    final int FDF3SIZE = 15;
	    final int FDF2LABEL = 16; // and 17: 8 ascii chars
	    final int FDF1LABEL = 18; // and 19: 8 ascii chars
	    final int FDF3LABEL = 20; // and 21: 8 ascii chars
	    final int FDF4LABEL = 22; // and 23: 8 ascii chars
	    final int FDDIMORDER1 = 24;
	    final int FDDIMORDER2 = 25;
	    final int FDDIMORDER3 = 26;
	    final int FDDIMORDER4 = 27;
	    final int FDF4OBS = 28;
	    final int FDF4SW = 29;
	    final int FDF4ORIG = 30;  // NDORIG is in hz so maybe this is too
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
	    final int FDF2ORIG = 101;  // NDORIG is in hz so maybe this is too
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
	    final int FDF1ORIG = 249;  // NDORIG is in hz so maybe this is too
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
	    final int FDSRCNAME = 286; // and 287 and 288 and 289: 16 ascii chars
	    final int FDUSERNAME = 290; // and 291 and 292 and 293: 16 ascii chars
	    final int FDMONTH = 294;
	    final int FDDAY = 295;
	    final int FDYEAR = 296;
	    final int FDTITLE = 297;  // through 311: 60 ascii chars
	    final int FDCOMMENT = 312; // through 351: 160 ascii chars
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
	    final int FDOPERNAME = 464; // through 471: 32 ascii chars
	    // 472-474 = ?
	    final int FDF1AQSIGN = 475;
	    final int FDF3AQSIGN = 476;
	    final int FDF4AQSIGN = 477;
	    final int FDSCALE = 478;
	    // 479 = ?
	    final int FDF2OFFPPM = 480;  // NDOFFPPM says this: "Additional PPM offset (for alignment)."
	    final int FDF1OFFPPM = 481;
	    final int FDF3OFFPPM = 482;
	    final int FDF4OFFPPM = 483;
	    // 484-511 = ?
	}
}
