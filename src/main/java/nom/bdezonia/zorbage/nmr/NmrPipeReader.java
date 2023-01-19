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
import java.util.Arrays;

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

		if (data.a().equals("real32")) {

			NdData<Float32Member> nd = new NdData<>(data.b(), data.c());
			
			bundle.flts.add(nd);
		}
		else if (data.a().equals("complex32")) {
			
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
			
			long[] dims = data.b();
			
			dims[dims.length-1] /= 2;
			
			System.out.println("NUMFLOATS = "+numFloats);
			
			System.out.println("dims = "+Arrays.toString(dims));
			
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

			FileReader reader = new FileReader();

			reader.readHeader(dis);

			Float32Member type = G.FLT.construct();
			
			for (long i = 0; i < numFloats; i++) {
			
				float val = reader.nextDataFloat(dis);
				
				type.setV(val);
				
				data.set(i, type);
			}

			String dataType = reader.findDataType();
			
			long[] dims = reader.findDims();
			
			return new Tuple3<>(dataType, dims, data);
			
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

	private static class FileReader {

		private int[] vars = new int[512];
		
		private boolean byteSwapNeeded = false;

		void readHeader(DataInputStream dis) throws IOException {

			for (int i = 0; i < vars.length; i++) {
				vars[i] = dis.readInt();
			}
			
			// endian check from a known header variable
			
			float headerVal = Float.intBitsToFloat(vars[FDFLTORDER]);
			
			byteSwapNeeded =  (headerVal - 2.345f > 1e-6);
		}
		
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

		private String findDataType() {
		
			String type = "real32";
			
			int dimCount = (int) getHeaderFloat(FDDIMCOUNT);
			
			if (dimCount < 1 || dimCount > 4)
				throw new IllegalArgumentException("crazy dim count "+dimCount);
			
			// TODO: use FTDIMORDERn constants to correctly reason about complex dimension?
			
			if (getHeaderFloat(FDQUADFLAG) == 1.0)
				type = "complex32";
				
			if (dimCount == 1)
				if (getHeaderFloat(FDF1QUADFLAG) == 1.0)
					type = "complex32";
			
			if (dimCount == 2)
				if (getHeaderFloat(FDF2QUADFLAG) == 1.0)
					type = "complex32";
			
			if (dimCount == 3)
				if (getHeaderFloat(FDF3QUADFLAG) == 1.0)
					type = "complex32";
			
			if (dimCount == 4)
				if (getHeaderFloat(FDF4QUADFLAG) == 1.0)
					type = "complex32";
			
			return type;
		}
		
		private long[] findDims() {
			
			// TODO: use FTDIMORDERn constants to correctly reason about shape?
			
			int dimCount = (int) getHeaderFloat(FDDIMCOUNT);
			
			if (dimCount < 1 || dimCount > 4)
 				throw new IllegalArgumentException("crazy dim count "+dimCount);
			
			long[] dims = new long[dimCount];
			
			if (dimCount >= 1)
				dims[0] = (long) getHeaderFloat(FDF1TDSIZE);

			if (dimCount >= 2)
				dims[1] = (long) getHeaderFloat(FDF2TDSIZE);

			if (dimCount >= 3)
				dims[2] = (long) getHeaderFloat(FDF3TDSIZE);

			if (dimCount == 4)
				dims[3] = (long) getHeaderFloat(FDF4TDSIZE);

			for (int i = 0; i < dims.length; i++) {
			
				if (dims[i] < 1 || dims[i] > 10000)
					throw new IllegalArgumentException("dims look shady: "+Arrays.toString(dims));
			}
			
			return dims;
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
