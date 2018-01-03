ftpObj = ftp(%'ftp ip','ftp username','ftp password'%);
aa = dir(ftpObj,'htdocs/uploads/*.txt');
[tmp,ind] = sort({aa.date});
A = aa(ind);
filenames = {A.name};
newStr = strrep(filenames(1),':','-');
rename(ftpObj,filenames(1),newStr);
disp(newStr);
mget(ftpObj,newStr);
fid = fopen(string(newStr));
%fid = fopen('C:\Users\Andy\Desktop\locationFileNew.txt');
i = 0;
P = geopoint();
while 1
    i = i + 1;
    tline = fgetl(fid);
    if ~ischar(tline), break, end
    if i>1
        if tline(26:26) == 'A'
            line =tline(29:length(tline)-1);
            C = (strsplit(line,','));
            if ~isempty(P)
                P = append(P,str2double(C{1}),str2double(C{2}));
            else
                P = geopoint(str2double(C{1}),str2double(C{2}));
            end
        end
    end
end
hold off;
wmline(P);
close all
fclose(fid);