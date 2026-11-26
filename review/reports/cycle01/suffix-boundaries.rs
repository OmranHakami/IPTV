use oh_domain::metadata::search_terms;

#[test]
fn suffix_and_year_rejection_paths() {
    for name in ["Film audio", "Film mono audio", "Film (1899)", "Film (2100)", "Film (0000)", "Film (20x4)", "Film (202)", "Film 4Kish"] {
        assert_eq!(search_terms(name, None), (name, None));
    }
    for (raw,clean,year) in [("Film (1900)", "Film",1900),("Film (2099) [UHD]", "Film",2099),("Film (2024) [720p] - DUAL AUDIO", "Film",2024)] {
        assert_eq!(search_terms(raw,None),(clean,Some(year)));
    }
}
