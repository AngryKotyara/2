#!/usr/bin/env python3
import socket
import struct
import subprocess
import sys
import tempfile
import threading
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/'project'
APP=ROOT/'app/src/main/java/com/auratv/diagnostics'
TEST=Path(__file__).with_name('StreamProtocolSmoke.java')
def packet(code,a0=0,a1=0,data=b''):
    code=int.from_bytes(code.encode(),'little')
    return struct.pack('<6I',code,a0,a1,len(data),sum(data)&0xffffffff,code^0xffffffff)+data
def read(s):
    def exact(n):
        result=b''
        while len(result)<n:
            got=s.recv(n-len(result))
            if not got:raise EOFError("mock ADB disconnected")
            result+=got
        return result
    header=exact(24)
    cmd,a0,a1,size,checksum,magic=struct.unpack('<6I',header)
    assert (cmd^0xffffffff)==magic
    data=exact(size)
    assert sum(data)&0xffffffff==checksum
    return cmd.to_bytes(4,'little').decode(),a0,a1,data

def server_run(s,errors):
    try:
        c,_=s.accept()
        with c:
            c.settimeout(15)
            assert read(c)[0]=='CNXN'
            c.sendall(packet('CNXN',0x01000000,4096,b'device::\0'))

            cmd,l1,_,service=read(c)
            assert (cmd,service)==('OPEN',b'shell:id -u\0')
            c.sendall(packet('OKAY',101,l1))
            c.sendall(packet('WRTE',101,l1,b'2000\n'))
            assert read(c)[0]=='OKAY'
            c.sendall(packet('CLSE',101,l1))
            assert read(c)[0]=='CLSE'

            cmd,l2,_,service=read(c)
            assert (cmd,service)==('OPEN',b'exec:cat /test/file1\0')
            # Delayed control messages for a closed earlier ADB shell:
            c.sendall(packet('CLSE',101,l1))
            c.sendall(packet('OKAY',101,l1))
            c.sendall(packet('OKAY',102,l2))
            c.sendall(packet('WRTE',102,l2,bytes([0,13,10,255,34])))
            assert read(c)[0]=='OKAY'
            c.sendall(packet('WRTE',102,l2,bytes([0,7,11,42])))
            assert read(c)[0]=='OKAY'
            c.sendall(packet('CLSE',102,l2))
            assert read(c)[0]=='CLSE'

            cmd,l3,_,service=read(c)
            assert (cmd,service)==('OPEN',b'exec:cat /test/file2\0')
            c.sendall(packet('CLSE',102,l2))
            c.sendall(packet('OKAY',103,l3))
            c.sendall(packet('WRTE',103,l3,bytes([99,88,0,10,13,254])))
            assert read(c)[0]=='OKAY'
            c.sendall(packet('CLSE',103,l3))
            assert read(c)[0]=='CLSE'
    except Exception as e:
        errors.append(e)
def main():
    s=socket.socket()
    s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
    s.bind(('127.0.0.1',0))
    s.listen(1)
    errors=[]
    t=threading.Thread(target=server_run,args=(s,errors),daemon=True)
    t.start()
    with tempfile.TemporaryDirectory() as tmp:
        cmd=['javac','-d',tmp,str(APP/'AdbClient.java'),str(APP/'AdbKey.java'),str(TEST)]
        subprocess.run(cmd,check=True,timeout=25)
        run=subprocess.run(['java','-cp',tmp,'StreamProtocolSmoke',tmp,str(s.getsockname()[1])],
                           text=True,capture_output=True,timeout=25)
        print(run.stdout)
        if run.returncode:print(run.stderr,file=sys.stderr)
        assert run.returncode==0, 'Java protocol test failed'
    t.join(timeout=10)
    s.close()
    if errors:raise errors[0]
    assert not t.is_alive(), "mock ADB server did not terminate"
if __name__=='__main__':main()
