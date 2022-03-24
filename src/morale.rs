use std::borrow::Borrow;
use std::path::Path;

use rayon::prelude::*;
use rusqlite::{Connection, Result};
use scraper::Html;
use scraper::Selector;

pub fn run_morale_officer() {
    let existing_posts = get_existing_posts();
    if existing_posts.is_empty() {
        println!("No cats found locally! Scraping...");
        scrape_new_morale();
        open_posts(get_existing_posts())
        // instead of raising the number, make sure you scrape atleast once per day
    } else if count_unopened_posts() < 300 {
        open_posts(existing_posts);
        scrape_new_morale();
    } else {
        open_posts(existing_posts);
    }
}

fn open_posts(posts: Vec<String>) {
    posts.iter().for_each(|post| {
        webbrowser::open(post.as_str()).unwrap()
    });
    mark_posts_as_opened(posts)
}

fn mark_posts_as_opened(posts: Vec<String>) {
    let conn = get_conn();
    posts.iter().for_each(|post| {
        let update = format!("UPDATE POSTS SET opened = 1 WHERE url == \"{}\"", post);
        let mut statement = conn.prepare(update.borrow()).unwrap();
        statement.execute([]).unwrap();
    })
}

pub fn setup_db() {
    let conn = get_conn();
    conn.execute("CREATE TABLE IF NOT EXISTS POSTS(
        id integer PRIMARY KEY,
        url varchar NOT NULL UNIQUE,
        upvotes integer NOT NULL,
        sub varchar NOT NULL,
        opened boolean NOT NULL CHECK (opened IN (0, 1)))", []).unwrap();
    conn.close().unwrap()
}

fn get_conn() -> Connection {
    let value = std::env::var("HOME").unwrap();
    let home_dir = Path::new(&value);
    let neelix_db = Path::new(".neelix.db");
    let joined = home_dir.join(neelix_db);
    Connection::open(joined.to_str().unwrap()).unwrap()
}

fn scrape_new_morale() {
    // todo create a struct that holds the sub and upvote count
    let subs = vec!["Floof",
                    "CatsWhoYell",
                    "Meow_Irl",
                    "Blep",
                    "DelightfullyChubby",
                    "CatBellies",
                    "CatsStandingUp",
                    "CatLoaf",
                    "GeometricCats",
                    "SuspiciousKitties",
                    "BigCats",
                    "IllegallySmolCats",
                    "CatsSittingDown",
                    "thecatdimension",
                    "TuckedInKitties",
                    "straightenedfeetsies",
                    "SneezingCats",
                    "bottlebrush",
                    "catswhotrill",
                    "TheCatTrapIsWorking",
                    "CatsISUOTTATFO",
                    "animalsbeingderps",
                    "notmycat"];
    let allowed_domains = vec!["i.redd.it", "v.redd.it", "old.reddit.com", "gfycat.com"];
    subs.par_iter().for_each(|sub| {
        println!("Scraping {}...", sub);
        let resp = reqwest::blocking::get(format!("https://old.reddit.com/r/{}", sub)).unwrap();

        let document = Html::parse_document(&resp.text().unwrap());
        let thing_selector = Selector::parse(".thing").unwrap();
        for thing in document.select(&thing_selector) {
            let url = thing.value().attr("data-url").unwrap();
            let upvotes: i32 = thing.value().attr("data-score").unwrap().to_string().parse().unwrap();
            let data_domain = thing.value().attr("data-domain").unwrap();
            // todo introduce upvote threshold for each subreddit
            if allowed_domains.contains(&data_domain) && upvotes > 500 {
                let conn = get_conn();
                // todo this can fail on url uniqueness, which is good
                // but how do I unwrap it, recognise that that's the error, and discard it?
                conn.execute("INSERT into POSTS (url, upvotes, opened, sub) values (?1, ?2, ?3, ?4)",
                             &[url, &upvotes.to_string(), &0.to_string(), sub]);
            }
        }
    });
}

fn get_existing_posts() -> Vec<String> {
    let conn = get_conn();
    let mut statement = conn.prepare("SELECT url FROM POSTS WHERE opened is 0 ORDER BY upvotes LIMIT 7").unwrap();
    let existing_posts: Vec<String> = statement
        .query_map([], |row| {
            let x = row.get_ref(0).unwrap().as_str().unwrap();
            Ok(x[..].to_owned())
        }).unwrap().map(|x| x.unwrap()).collect();
    existing_posts
}

fn count_unopened_posts() -> i64 {
    let conn = get_conn();
    let result: i64 = conn.query_row(
        "SELECT COUNT(*) FROM POSTS WHERE opened IS 0",
        [],
        |r| r.get(0),
    ).unwrap();
    // println!("Number of unopened posts: {}", result);
    result
}