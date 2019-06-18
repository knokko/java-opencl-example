package nl.knokko.test;

import static org.lwjgl.opencl.CL10.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Scanner;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CLContextCallbackI;
import org.lwjgl.system.MemoryUtil;

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
        PointerBuffer platformsBuffer = PointerBuffer.allocateDirect(1);
        IntBuffer platformAmountBuffer = BufferUtils.createIntBuffer(1);
        int errorCode = CL10.clGetPlatformIDs(platformsBuffer, platformAmountBuffer);
        checkCLError("Get platforms IDs", errorCode);
        int amountOfPlatforms = platformAmountBuffer.get();
        if (amountOfPlatforms < 1) {
        	throw new UnsupportedOperationException("No OpenCL platforms are available");
        }
        long platform = platformsBuffer.get();
        
        // 1000 should be enough
        PointerBuffer devicesBuffer = PointerBuffer.allocateDirect(1000);
        IntBuffer deviceAmountBuffer = BufferUtils.createIntBuffer(1);
        errorCode = CL10.clGetDeviceIDs(platform, CL10.CL_DEVICE_TYPE_GPU, devicesBuffer, deviceAmountBuffer);
        checkCLError("Get device IDs", errorCode);
        int amountOfDevices = deviceAmountBuffer.get();
        System.out.println("number of platforms is " + amountOfPlatforms + " and number of devices is " + amountOfDevices);
        System.out.println("platform is " + platform);
        long device = devicesBuffer.get(0);
        System.out.println("the device is " + device);
        
        IntBuffer errorCodeBuffer = BufferUtils.createIntBuffer(1);
        long context = CL10.clCreateContext((PointerBuffer) null, device, (CLContextCallbackI) null, MemoryUtil.NULL, (IntBuffer) null);
        checkError("Create context", errorCodeBuffer);
        
        long commandQueue = CL10.clCreateCommandQueue(context, device, CL10.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE, errorCodeBuffer);
        checkError("Create command queue", errorCodeBuffer);
        
        long memA = CL10.clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, A, errorCodeBuffer);
        checkError("Create A mem buffer", errorCodeBuffer);
        long memB = CL10.clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, B, errorCodeBuffer);
        checkError("Create B mem buffer", errorCodeBuffer);
        IntBuffer resultBuffer = BufferUtils.createIntBuffer(5);
        long memResult = CL10.clCreateBuffer(context, CL_MEM_WRITE_ONLY | CL_MEM_COPY_HOST_PTR, resultBuffer, errorCodeBuffer);
        checkError("Create result mem buffer", errorCodeBuffer);
        
        errorCode = CL10.clFinish(commandQueue);
        checkCLError("Finish creating mem buffers", errorCode);
        
        errorCode = CL10.clEnqueueWriteBuffer(commandQueue, memA, true, 0, A, null, null);
        checkCLError("Enqueue write buffer A", errorCode);
        errorCode = CL10.clEnqueueWriteBuffer(commandQueue, memB, true, 0, B, null, null);
        checkCLError("Enqueue write buffer B", errorCode);
        errorCode = CL10.clFinish(commandQueue);
        checkCLError("Finish writing bufferA and B", errorCode);
        
        long program = CL10.clCreateProgramWithSource(context, loadCLSource("array_add"), errorCodeBuffer);
        checkError("Create program with source", errorCodeBuffer);
        
        errorCode = CL10.clBuildProgram(program, device, "", null, MemoryUtil.NULL);
        
        if (errorCode != CL10.CL_SUCCESS) {
        	System.out.println("program build trouble:");
        	ByteBuffer paramValue = BufferUtils.createByteBuffer(10000);
        	CL10.clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, paramValue, PointerBuffer.allocateDirect(1));
        	for (int index = 0; index < paramValue.capacity(); index++) {
        		if (paramValue.get(index) == 0) {
        			break;
        		}
        		System.out.print((char) paramValue.get(index));
        	}
        	CL.destroy();
        	System.out.println("build program error code is " + errorCode);
        	return;
        }
        
        long kernel = CL10.clCreateKernel(program, "array_add", errorCodeBuffer);
        checkError("Create kernel", errorCodeBuffer);
        
        errorCode = CL10.clSetKernelArg1p(kernel, 0, memA);
        checkCLError("set kernel arg A", errorCode);
        errorCode = CL10.clSetKernelArg1p(kernel, 1, memB);
        checkCLError("set kernel arg B", errorCode);
        errorCode = CL10.clSetKernelArg1p(kernel, 2, memResult);
        checkCLError("set kernel arg answer", errorCode);
        
        PointerBuffer globalSize = BufferUtils.createPointerBuffer(1);
        globalSize.put(0, 5);
        
        errorCode = CL10.clFinish(commandQueue);
        checkCLError("Finish setting kernel args", errorCode);
        
        errorCode = CL10.clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, globalSize, null, null, null);
        checkCLError("Enqueue range kernel", errorCode);
        
        errorCode = CL10.clEnqueueReadBuffer(commandQueue, memResult, true, 0, resultBuffer, null, null);
        checkCLError("Read answer", errorCode);
        
        errorCode = CL10.clFinish(commandQueue);
        checkCLError("Finish", errorCode);
        
        System.out.println("Result:");
        System.out.println(intBufToString(A) + " + " + intBufToString(B) + " = " + intBufToString(resultBuffer));
        
        clReleaseKernel(kernel);
        clReleaseProgram(program);
        clReleaseMemObject(memA);
        clReleaseMemObject(memB);
        clReleaseMemObject(memResult);
        clReleaseCommandQueue(commandQueue);
        clReleaseContext(context);
        CL.destroy();
	}
	
	public static void checkError(String action, IntBuffer errorCodeBuffer) {
		checkCLError(action, errorCodeBuffer.get(0));
	}
	
	public static void checkCLError(String action, int errorCode) {
		if (errorCode != CL10.CL_SUCCESS) {
			CL.destroy();
			throw new RuntimeException("Get error code " + errorCode + " for action " + action);
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
