package config

// Server is the server section of the config
type Server struct {
	URL string `yaml:"url"`
}

// Config describes the schema of a config file
type Config struct {
	Server Server
}
