package nl.knokko.test;

import static org.lwjgl.opencl.CL10.CL_DEVICE_TYPE_GPU;
import static org.lwjgl.opencl.CL10.CL_MEM_COPY_HOST_PTR;
import static org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY;
import static org.lwjgl.opencl.CL10.CL_MEM_WRITE_ONLY;
import static org.lwjgl.opencl.CL10.CL_PROGRAM_BUILD_LOG;
import static org.lwjgl.opencl.CL10.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
import static org.lwjgl.opencl.CL10.CL_SUCCESS;
import static org.lwjgl.opencl.CL10.clBuildProgram;
import static org.lwjgl.opencl.CL10.clCreateBuffer;
import static org.lwjgl.opencl.CL10.clCreateCommandQueue;
import static org.lwjgl.opencl.CL10.clCreateContext;
import static org.lwjgl.opencl.CL10.clCreateKernel;
import static org.lwjgl.opencl.CL10.clCreateProgramWithSource;
import static org.lwjgl.opencl.CL10.clEnqueueNDRangeKernel;
import static org.lwjgl.opencl.CL10.clEnqueueReadBuffer;
import static org.lwjgl.opencl.CL10.clEnqueueWriteBuffer;
import static org.lwjgl.opencl.CL10.clFinish;
import static org.lwjgl.opencl.CL10.clGetDeviceIDs;
import static org.lwjgl.opencl.CL10.clGetPlatformIDs;
import static org.lwjgl.opencl.CL10.clGetProgramBuildInfo;
import static org.lwjgl.opencl.CL10.clReleaseCommandQueue;
import static org.lwjgl.opencl.CL10.clReleaseContext;
import static org.lwjgl.opencl.CL10.clReleaseKernel;
import static org.lwjgl.opencl.CL10.clReleaseMemObject;
import static org.lwjgl.opencl.CL10.clReleaseProgram;
import static org.lwjgl.opencl.CL10.clSetKernelArg1p;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Scanner;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL;
import org.lwjgl.opencl.CLContextCallbackI;
import org.lwjgl.system.MemoryStack;

public class CLArrayAdd {
	
	private static final IntBuffer A = BufferUtils.createIntBuffer(5).put(1).put(2).put(3).put(4).put(5);
	private static final IntBuffer B = BufferUtils.createIntBuffer(5).put(1).put(2).put(3).put(4).put(5);
	
	static {
		A.flip();
		B.flip();
	}
	
	public static void main(String[] args) {
		if (CL.getFunctionProvider() != null) {
			CL.destroy();
		}
		CL.create();
		
		long platform;
		try (MemoryStack stack = stackPush()) {
			
			IntBuffer numPlatforms = stack.mallocInt(1);
			assertSuccess(clGetPlatformIDs(null, numPlatforms));
			PointerBuffer platforms = stack.mallocPointer(numPlatforms.get(0));
			assertSuccess(clGetPlatformIDs(platforms, numPlatforms));
			
			// Just get the first platform
			platform = platforms.get();
		}
		
		long device;
		try (MemoryStack stack = stackPush()) {
			
			int deviceType = CL_DEVICE_TYPE_GPU;
			IntBuffer numDevices = stack.mallocInt(1);
			assertSuccess(clGetDeviceIDs(platform, deviceType, null, numDevices));
			PointerBuffer devices = stack.mallocPointer(numDevices.get(0));
			assertSuccess(clGetDeviceIDs(platform, deviceType, devices, numDevices));
			
			// For now, just get the first device
			device = devices.get(0);
		}
		
		try(MemoryStack stack = stackPush()) {
			
			IntBuffer error = stack.mallocInt(1);
			long context = clCreateContext((PointerBuffer) null, device, (CLContextCallbackI) null, NULL, error);
			assertSuccess(error);
			
			long queue = clCreateCommandQueue(context, device, CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE, error);
			assertSuccess(error);
			
			long memA = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, A, error);
			assertSuccess(error);
			
			long memB = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, B, error);
			assertSuccess(error);
			
			IntBuffer resultBuffer = stack.mallocInt(A.capacity());
			long memResult = clCreateBuffer(context, CL_MEM_WRITE_ONLY | CL_MEM_COPY_HOST_PTR, resultBuffer, error);
			assertSuccess(error);
			
			assertSuccess(clFinish(queue));
			
			assertSuccess(clEnqueueWriteBuffer(queue, memA, true, 0, A, null, null));
			assertSuccess(clEnqueueWriteBuffer(queue, memB, true, 0, B, null, null));
			assertSuccess(clFinish(queue));
			
			long program = clCreateProgramWithSource(context, loadCLSource("array_add"), error);
			assertSuccess(error);
			int buildErrorCode = clBuildProgram(program, device, "", null, NULL);
			if (buildErrorCode != CL_SUCCESS) {
				
				System.out.println("Failed to build cl program:");
				PointerBuffer logSize = stack.mallocPointer(1);
				assertSuccess(clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, (ByteBuffer) null, logSize));
				ByteBuffer log = stack.malloc((int) logSize.get(0));
				assertSuccess(clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, log, logSize));
				
				for (int logIndex = 0; logIndex < log.capacity(); logIndex++) {
					
					// Null-terminated
					if (log.get(logIndex) == 0) {
						break;
					}
					
					System.out.print((char) log.get(logIndex));
				}
				
				CL.destroy();
				return;
			}
			
			long kernel = clCreateKernel(program, "array_add", error);
			assertSuccess(error);
			
			assertSuccess(clSetKernelArg1p(kernel, 0, memA));
			assertSuccess(clSetKernelArg1p(kernel, 1, memB));
			assertSuccess(clSetKernelArg1p(kernel, 2, memResult));
			assertSuccess(clFinish(queue));
			
			PointerBuffer globalSize = stack.pointers(5);
			assertSuccess(clEnqueueNDRangeKernel(queue, kernel, 1, null, globalSize, null, null, null));
			
			assertSuccess(clEnqueueReadBuffer(queue, memResult, true, 0, resultBuffer, null, null));
			assertSuccess(clFinish(queue));
			
			System.out.println("Result:");
	        System.out.println(intBufToString(A) + " + " + intBufToString(B) + " = " + intBufToString(resultBuffer));
	        
	        clReleaseKernel(kernel);
	        clReleaseProgram(program);
	        clReleaseMemObject(memA);
	        clReleaseMemObject(memB);
	        clReleaseMemObject(memResult);
	        clReleaseCommandQueue(queue);
	        clReleaseContext(context);
	        CL.destroy();
		}
	}
	
	private static void assertSuccess(int errorCode) {
		if (errorCode != CL_SUCCESS) {
			CL.destroy();
			throw new RuntimeException("Got error code " + errorCode);
		}
	}
	
	private static void assertSuccess(IntBuffer error) {
		if (error.get(0) != CL_SUCCESS) {
			CL.destroy();
			throw new RuntimeException("Got error code " + error.get(0));
		}
	}
	
	static String intBufToString(IntBuffer buffer) {
		String result = "[";
		while (buffer.remaining() > 1) {
			result += buffer.get() + ",";
		}
		if (buffer.hasRemaining()) {
			result += buffer.get();
		}
		return result + "]";
	}
	
	public static String loadCLSource(String fileName) {
		String result = "";
		Scanner scanner = new Scanner(CLArrayAdd.class.getClassLoader().getResourceAsStream("nl/knokko/test/" + fileName + ".cl"));
		while (scanner.hasNextLine()) {
			result += scanner.nextLine();
			result += '\n';
		}
		scanner.close();
		return result;
	}
}
