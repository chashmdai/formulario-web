#define _GNU_SOURCE
#include <linux/landlock.h>
#include <linux/seccomp.h>
#include <linux/filter.h>
#include <linux/audit.h>
#include <sys/syscall.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <stddef.h>
#include <stdio.h>
#include <stdint.h>
static void die(void){fputs("Aislamiento no disponible\n",stderr);_exit(125);}
static void rule(int fd,const char *path,uint64_t rights){int p=open(path,O_PATH|O_CLOEXEC);if(p<0)die();struct landlock_path_beneath_attr a={.allowed_access=rights,.parent_fd=p};if(syscall(SYS_landlock_add_rule,fd,LANDLOCK_RULE_PATH_BENEATH,&a,0))die();close(p);}
int main(int argc,char**argv){
 if(argc<3)return 125;
 if(syscall(SYS_landlock_create_ruleset,NULL,0,LANDLOCK_CREATE_RULESET_VERSION)<3)die();
 uint64_t read=LANDLOCK_ACCESS_FS_READ_FILE|LANDLOCK_ACCESS_FS_READ_DIR;
 struct landlock_ruleset_attr a={.handled_access_fs=(1ULL<<15)-1};int fd=syscall(SYS_landlock_create_ruleset,&a,sizeof(a),0);if(fd<0)die();
 rule(fd,"/usr",read|LANDLOCK_ACCESS_FS_EXECUTE);rule(fd,"/lib",read|LANDLOCK_ACCESS_FS_EXECUTE);if(access("/lib64",F_OK)==0)rule(fd,"/lib64",read|LANDLOCK_ACCESS_FS_EXECUTE);rule(fd,"/opt",read|LANDLOCK_ACCESS_FS_EXECUTE);rule(fd,"/etc",read);
 rule(fd,"/dev/null",LANDLOCK_ACCESS_FS_READ_FILE|LANDLOCK_ACCESS_FS_WRITE_FILE);rule(fd,"/dev/urandom",LANDLOCK_ACCESS_FS_READ_FILE);
 rule(fd,argv[1],read|LANDLOCK_ACCESS_FS_WRITE_FILE|LANDLOCK_ACCESS_FS_REMOVE_DIR|LANDLOCK_ACCESS_FS_REMOVE_FILE|LANDLOCK_ACCESS_FS_MAKE_DIR|LANDLOCK_ACCESS_FS_MAKE_REG|LANDLOCK_ACCESS_FS_REFER|LANDLOCK_ACCESS_FS_TRUNCATE);
 if(prctl(PR_SET_NO_NEW_PRIVS,1,0,0,0)||syscall(SYS_landlock_restrict_self,fd,0)){die();}
 close(fd);
 struct sock_filter filter[]={
  BPF_STMT(BPF_LD|BPF_W|BPF_ABS,offsetof(struct seccomp_data,arch)),
#if defined(__x86_64__)
  BPF_JUMP(BPF_JMP|BPF_JEQ|BPF_K,AUDIT_ARCH_X86_64,1,0),
#elif defined(__aarch64__)
  BPF_JUMP(BPF_JMP|BPF_JEQ|BPF_K,AUDIT_ARCH_AARCH64,1,0),
#else
#error Arquitectura sin verificar
#endif
  BPF_STMT(BPF_RET|BPF_K,SECCOMP_RET_KILL_PROCESS),
  BPF_STMT(BPF_LD|BPF_W|BPF_ABS,offsetof(struct seccomp_data,nr)),
#if defined(__x86_64__)
  BPF_JUMP(BPF_JMP|BPF_JGE|BPF_K,0x40000000,0,1),BPF_STMT(BPF_RET|BPF_K,SECCOMP_RET_ERRNO|EPERM),
#endif
#define DENY(n) BPF_JUMP(BPF_JMP|BPF_JEQ|BPF_K,__NR_##n,0,1),BPF_STMT(BPF_RET|BPF_K,SECCOMP_RET_ERRNO|EPERM)
  DENY(io_uring_setup),DENY(io_uring_enter),DENY(io_uring_register),DENY(pidfd_getfd),DENY(socket),DENY(connect),DENY(socketpair),DENY(ptrace),DENY(process_vm_readv),DENY(process_vm_writev),DENY(kill),DENY(tkill),DENY(tgkill),DENY(setsid),DENY(setpgid),DENY(mount),DENY(umount2),DENY(unshare),DENY(setns),
  BPF_STMT(BPF_RET|BPF_K,SECCOMP_RET_ALLOW)
 };
 struct sock_fprog prog={.len=sizeof(filter)/sizeof(filter[0]),.filter=filter};if(prctl(PR_SET_SECCOMP,SECCOMP_MODE_FILTER,&prog))die();
 struct rlimit cpu={45,45},as={768ULL*1024*1024,768ULL*1024*1024},fs={16ULL*1024*1024,16ULL*1024*1024},nofile={64,64};
 if(setrlimit(RLIMIT_CPU,&cpu)||setrlimit(RLIMIT_AS,&as)||setrlimit(RLIMIT_FSIZE,&fs)||setrlimit(RLIMIT_NOFILE,&nofile))die();
 execvp(argv[2],argv+2);die();
}
