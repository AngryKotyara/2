import com.auratv.diagnostics.AdbClient;
import com.auratv.diagnostics.AdbKey;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;

public final class StreamProtocolSmoke {
    public static void main(String[] args) throws Exception {
        byte[] binary1=new byte[]{0,13,10,(byte)255,34,0,7,11,42};
        byte[] binary2=new byte[]{99,88,0,10,13,(byte)254};
        AdbKey key=AdbKey.loadOrCreate(new File(args[0]));
        try(AdbClient adb=new AdbClient()){
            adb.connect("127.0.0.1",Integer.parseInt(args[1]),key,
                msg->{});
            String uid=adb.shell("id -u",120);
            if(!uid.equals("2000"))throw new Exception("ADB shell check: "+uid);
            ByteArrayOutputStream buf1=new ByteArrayOutputStream();
            long n1=adb.execToStream("cat /test/file1",buf1,binary1.length,null);
            if(n1!=binary1.length || !Arrays.equals(binary1,buf1.toByteArray()))
                throw new Exception("Binary transfer 1 corrupted");
            ByteArrayOutputStream buf2=new ByteArrayOutputStream();
            long n2=adb.execToStream("cat /test/file2",buf2,binary2.length,null);
            if(n2!=binary2.length || !Arrays.equals(binary2,buf2.toByteArray()))
                throw new Exception("Binary transfer 2 corrupted");
            System.out.println("PASS: shell + 2 raw ADB transfers with stale control packets");
        }
    }
}
