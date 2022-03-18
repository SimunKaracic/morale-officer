mod morale;

use crate::morale::*;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    setup_db();
    run_morale_officer();

    Ok(())
}
