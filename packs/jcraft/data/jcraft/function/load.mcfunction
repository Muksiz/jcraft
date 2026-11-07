# Runs on every server start and /reload, so these rules can't drift from the repo.

# One sleeping player skips the night.
gamerule players_sleeping_percentage 1

# Default random tick speed. It is sometimes raised temporarily (e.g. to let snow build up);
# this puts it back on the next start.
gamerule random_tick_speed 3
