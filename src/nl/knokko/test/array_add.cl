kernel void array_add(global const int *A, global const int *B, global int *dest){
	unsigned int id = get_global_id(0);
	dest[id] = A[id] + B[id];
}