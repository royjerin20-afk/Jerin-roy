#include <stdio.h>
#include <unistd.h>
int main(){
pid_t pid;
pid=fork();
if(pid<0){
perror ("fork failed");
return 1;}
if (pid == 0) {
printf("Child Process:\n");
printf(" PID = %d\n", getpid());
printf(" PPID = %d\n", getppid());
} else {
printf("Parent Process:\n");
printf(" PID = %d\n", getpid());
printf(" Child PID = %d\n", pid);}
return 0;}
