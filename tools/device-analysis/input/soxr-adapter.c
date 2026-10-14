/* Stateful bounded adapter. SPDX-License-Identifier: LGPL-2.1-or-later */
#include <stdint.h>
#include <stdlib.h>
#include <math.h>
#include "soxr.h"
#define CAPACITY 16384
static float input[CAPACITY],output[CAPACITY];
static soxr_t state;
static size_t used,produced;
static int failed,flushing;
void dispose(void){if(state)soxr_delete(state);state=0;used=produced=0;failed=flushing=0;}
int initialize(unsigned rate){
 dispose();if(rate<8000||rate>96000)return 0;
 soxr_error_t error=0;
 soxr_io_spec_t io=soxr_io_spec(SOXR_FLOAT32_I,SOXR_FLOAT32_I);
 soxr_quality_spec_t quality=soxr_quality_spec(SOXR_HQ,0);
 soxr_runtime_spec_t runtime=soxr_runtime_spec(1);
 state=soxr_create(rate,22050,1,&error,&io,&quality,&runtime);
 if(error||!state){dispose();return 0;}return 1;
}
uintptr_t input_ptr(void){return (uintptr_t)input;}
uintptr_t output_ptr(void){return (uintptr_t)output;}
unsigned input_used(void){return used;}
unsigned output_length(void){return produced;}
int process(unsigned count,int end){
 used=produced=0;
 if(!state||failed||count>CAPACITY||(end&&count)||(flushing&&!end))return 0;
 for(unsigned i=0;i<count;i++)if(!isfinite(input[i])){failed=1;return 0;}
 if(end)flushing=1;
 soxr_error_t error=soxr_process(state,end?0:input,count,&used,output,CAPACITY,&produced);
 if(error){failed=1;return 0;}return 1;
}
