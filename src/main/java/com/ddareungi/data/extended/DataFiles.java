package com.ddareungi.data.extended;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.HexFormat;
import java.util.zip.ZipInputStream;
import java.nio.charset.Charset;

public final class DataFiles {
    private DataFiles(){}
    public static java.io.BufferedReader csvReader(Path path,Charset fallback)throws Exception {
        var header=new java.io.ByteArrayOutputStream();
        try(var input=Files.newInputStream(path)){
            int value;while((value=input.read())!=-1 && value!='\n' && value!='\r'){
                if(header.size()>=8192)throw new IllegalArgumentException("CSV_HEADER_TOO_LONG");
                header.write(value);
            }
        }
        Charset charset=fallback;
        try{
            String text=java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(header.toByteArray())).toString();
            if(text.contains("기준_날짜")||text.contains("대여일자"))charset=java.nio.charset.StandardCharsets.UTF_8;
        }catch(java.nio.charset.CharacterCodingException ignored){ /* Legacy official MS949 file. */ }
        var reader=Files.newBufferedReader(path,charset);
        try{reader.mark(1);if(reader.read()!=0xfeff)reader.reset();return reader;}
        catch(Exception error){reader.close();throw error;}
    }
    public static String checksum(Path path)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        try(var input=new DigestInputStream(Files.newInputStream(path),digest)){input.transferTo(java.io.OutputStream.nullOutputStream());}
        return HexFormat.of().formatHex(digest.digest());
    }
    public static long copyLimited(InputStream input,Path output,long limit)throws Exception {
        long total=0;byte[] buffer=new byte[65536];
        try(var target=Files.newOutputStream(output)){int size;while((size=input.read(buffer))!=-1){total+=size;if(total>limit)throw new IllegalArgumentException("FILE_SIZE_LIMIT");target.write(buffer,0,size);}}
        catch(Exception error){Files.deleteIfExists(output);throw error;}
        return total;
    }
    public static int extractOdZip(Path archive,Path directory,long limit)throws Exception {
        Files.createDirectories(directory);int count=0;long total=0;
        Path staging=Files.createTempDirectory(directory,"od-stage-");
        try {
        try(var zip=new ZipInputStream(Files.newInputStream(archive),Charset.forName("MS949"))){
            java.util.zip.ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                String name=entry.getName();
                if(entry.isDirectory())continue;
                if(!name.matches("tpss_bcycl_od_statnhm_[0-9]{8}\\.csv"))throw new IllegalArgumentException("UNEXPECTED_ZIP_ENTRY");
                if(count>=366)throw new IllegalArgumentException("ZIP_ENTRY_LIMIT");
                Path target=staging.resolve(name);
                if(Files.exists(target))throw new IllegalArgumentException("DUPLICATE_ZIP_ENTRY");
                total+=copyLimited(zip,target,limit-total);count++;
            }
        }
        if(count==0)throw new IllegalArgumentException("EMPTY_ZIP");
        try(var staged=Files.list(staging)){for(Path file:staged.toList())Files.move(file,directory.resolve(file.getFileName()),StandardCopyOption.REPLACE_EXISTING);}
        return count;
        }finally{try(var staged=Files.list(staging)){for(Path file:staged.toList())Files.deleteIfExists(file);}Files.deleteIfExists(staging);}
    }
}
