import pathlib, shutil, subprocess, tempfile
root = pathlib.Path.cwd()
channel = """        self.kind == Kind::Live
            && self.account == row.key.account
            && (self.category.is_none() || self.category == row.category_id)"""
assert channel in (root / "src/discovery.rs").read_text()
cases = [
    ("channel-always-true","discovery.rs",channel,"        true","channel_filter_rejects_wrong_account_kind_category"),
    ("channel-category-or","discovery.rs",channel,channel.replace("&& (self.category","|| (self.category"),"channel_filter_rejects_wrong_account_kind_category"),
    ("channel-account-or","discovery.rs",channel,channel.replace("&& self.account","|| self.account"),"channel_filter_rejects_wrong_account_kind_category"),
    ("date-separator-or-to-and","metadata.rs","bytes[4] != b'-' || bytes[7] != b'-'","bytes[4] != b'-' && bytes[7] != b'-'","second_separator_and_year_length_rejection"),
    ("year-length-and-to-or","metadata.rs","year.len() == 4 && year.bytes().all(|c| c.is_ascii_digit())","year.len() == 4 || year.bytes().all(|c| c.is_ascii_digit())","second_separator_and_year_length_rejection"),
]
for name,file,old,new,test in cases:
    with tempfile.TemporaryDirectory() as d:
        p=pathlib.Path(d)
        for folder in ["src","tests"]: shutil.copytree(root/folder,p/folder)
        for file2 in ["Cargo.toml","Cargo.lock"]: shutil.copy2(root/file2,p/file2)
        source=p/"src"/file
        data=source.read_text()
        assert data.count(old)==1, (name,data.count(old))
        source.write_text(data.replace(old,new,1))
        cmd=["cargo","+1.100.0","test","--locked","--test","mutation_followup",test,"--","--exact"]
        result=subprocess.run(cmd,cwd=p,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=90)
        print(name, "exit",result.returncode,flush=True)
        print(result.stdout,flush=True)
        assert result.returncode != 0 and "test result: FAILED" in result.stdout, name
        print("KILLED",name,flush=True)
print("FOLLOWUP RESULT 5/5 KILLED",flush=True)
