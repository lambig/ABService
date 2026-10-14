/* Bounded FLAC input adapter. SPDX-License-Identifier: MIT */
#include <stdint.h>
#include <stdlib.h>
#include "FLAC/stream_decoder.h"
__attribute__((import_module("input"),import_name("read"))) extern int host_read(uintptr_t,size_t);
__attribute__((import_module("input"),import_name("seek"))) extern int host_seek(uint64_t);
__attribute__((import_module("input"),import_name("tell"))) extern uint64_t host_tell(void);
__attribute__((import_module("input"),import_name("length"))) extern uint64_t host_length(void);
static FLAC__StreamDecoder *decoder;
static float mono[65535];
static unsigned count,rate,channels,bits,failed;
static uint64_t samples;
static FLAC__StreamDecoderReadStatus read_cb(const FLAC__StreamDecoder*d,FLAC__byte*b,size_t*n,void*x){
 int got=host_read((uintptr_t)b,*n); if(got<0){failed=1;return FLAC__STREAM_DECODER_READ_STATUS_ABORT;}
 *n=(size_t)got;return got?FLAC__STREAM_DECODER_READ_STATUS_CONTINUE:FLAC__STREAM_DECODER_READ_STATUS_END_OF_STREAM;
}
static FLAC__StreamDecoderSeekStatus seek_cb(const FLAC__StreamDecoder*d,FLAC__uint64 p,void*x){return host_seek(p)?FLAC__STREAM_DECODER_SEEK_STATUS_ERROR:FLAC__STREAM_DECODER_SEEK_STATUS_OK;}
static FLAC__StreamDecoderTellStatus tell_cb(const FLAC__StreamDecoder*d,FLAC__uint64*p,void*x){*p=host_tell();return FLAC__STREAM_DECODER_TELL_STATUS_OK;}
static FLAC__StreamDecoderLengthStatus length_cb(const FLAC__StreamDecoder*d,FLAC__uint64*p,void*x){*p=host_length();return FLAC__STREAM_DECODER_LENGTH_STATUS_OK;}
static FLAC__bool eof_cb(const FLAC__StreamDecoder*d,void*x){return host_tell()>=host_length();}
static void metadata_cb(const FLAC__StreamDecoder*d,const FLAC__StreamMetadata*m,void*x){
 if(m->type!=FLAC__METADATA_TYPE_STREAMINFO)return;
 rate=m->data.stream_info.sample_rate;channels=m->data.stream_info.channels;bits=m->data.stream_info.bits_per_sample;samples=m->data.stream_info.total_samples;
 if(rate<8000||rate>96000||channels<1||channels>2||(bits!=16&&bits!=24)||!samples||samples>(uint64_t)rate*14400)failed=1;
}
static void error_cb(const FLAC__StreamDecoder*d,FLAC__StreamDecoderErrorStatus s,void*x){failed=1;}
static FLAC__StreamDecoderWriteStatus write_cb(const FLAC__StreamDecoder*d,const FLAC__Frame*f,const FLAC__int32*const b[],void*x){
 if(failed||f->header.blocksize>65535||f->header.sample_rate!=rate||f->header.channels!=channels||f->header.bits_per_sample!=bits){failed=1;return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;}
 count=f->header.blocksize;
 const float scale=bits==16?1.0f/32768:1.0f/8388608;
 for(unsigned i=0;i<count;i++){
  float a=(float)b[0][i]*scale;
  mono[i]=channels==1?a:(a+(float)b[1][i]*scale)*0.5f;
 }
 return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE;
}
void dispose(void){if(decoder){FLAC__stream_decoder_delete(decoder);decoder=0;}count=rate=channels=bits=failed=0;samples=0;}
int initialize(void){
 dispose();decoder=FLAC__stream_decoder_new();if(!decoder)return 0;
 FLAC__stream_decoder_set_metadata_ignore_all(decoder);
 FLAC__stream_decoder_set_metadata_respond(decoder,FLAC__METADATA_TYPE_STREAMINFO);
 FLAC__stream_decoder_set_md5_checking(decoder,1);
 if(FLAC__stream_decoder_init_stream(decoder,read_cb,seek_cb,tell_cb,length_cb,eof_cb,write_cb,metadata_cb,error_cb,0)!=FLAC__STREAM_DECODER_INIT_STATUS_OK||!FLAC__stream_decoder_process_until_end_of_metadata(decoder)||failed||!samples){dispose();return 0;}
 return 1;
}
/* 1 = block ready, 0 = EOF, -1 = failure. Seek may leave a partial block ready. */
int next_block(void){
 if(!decoder||failed)return -1;
 if(count)return 1;
 while(FLAC__stream_decoder_get_state(decoder)!=FLAC__STREAM_DECODER_END_OF_STREAM){
  if(!FLAC__stream_decoder_process_single(decoder)||failed){count=0;failed=1;return -1;}
  if(count)return 1;
 }
 return 0;
}
int seek_sample(uint64_t position){
 count=0;if(!decoder||failed||position>=samples)return 0;
 if(!FLAC__stream_decoder_seek_absolute(decoder,position)||failed){count=0;failed=1;return 0;}return 1;
}
uintptr_t output(void){return (uintptr_t)mono;}
unsigned output_length(void){return count;}
void consume(void){count=0;}
unsigned sample_rate(void){return rate;}
unsigned channel_count(void){return channels;}
unsigned bit_depth(void){return bits;}
uint64_t sample_count(void){return samples;}
/* Must be called after sequential EOF; libFLAC disables MD5 after a seek. */
int finish(void){if(!decoder||failed)return 0;count=0;return FLAC__stream_decoder_finish(decoder);}
